package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.payment.pawapay.DepositOutcome;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.PaymentTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Traitement des dons publics (PaymentTransaction) : côté back-office, confirmation manuelle d'un
 * don en mode manuel (crée la Contribution) ou annulation ; côté prestataire, application
 * idempotente du résultat rapporté par PawaPay ({@link #applyProviderOutcome}). Aucune permission
 * dédiée : réutilise MANAGE_CONTRIBUTIONS (voir web.PaymentTransactionController).
 * <p>
 * Invariants RG-20 / RG-07 / RG-17 tenus ici :
 * <ul>
 *   <li>une PaymentTransaction crée au plus UNE Contribution, dans la même transaction SQL que son
 *       passage à CONFIRMED ;</li>
 *   <li>un statut final (CONFIRMED, FAILED, CANCELLED) n'évolue plus ; toute nouvelle information
 *       du prestataire sur un don déjà final est ignorée (ou journalisée comme anomalie) ;</li>
 *   <li>tout changement d'état issu du prestataire se fait sous verrou de ligne (FOR UPDATE), de
 *       sorte que callback et polling ne puissent pas s'appliquer en parallèle ;</li>
 *   <li>un don en ligne ne se confirme jamais à la main : seule la lecture du statut chez PawaPay
 *       fait foi (sinon un opérateur pourrait créer une Contribution sans argent reçu).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionService.class);
    private static final Duration FIRST_CHECK_DELAY = Duration.ofSeconds(15);
    private static final Duration MAX_CHECK_DELAY = Duration.ofMinutes(5);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ContributionRepository contributionRepository;
    private final DonorRepository donorRepository;
    private final AuditLogService auditLogService;
    private final PawaPayProperties pawaPayProperties;

    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> search(PaymentTransactionStatus status, Pageable pageable) {
        Page<PaymentTransaction> page = status != null
            ? paymentTransactionRepository.findByStatus(status, pageable)
            : paymentTransactionRepository.findAll(pageable);
        return page.map(PaymentTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse get(Long id) {
        return PaymentTransactionResponse.from(find(id));
    }

    /** Confirmation manuelle (mode manuel uniquement) après vérification humaine de la réception des fonds. */
    public PaymentTransactionResponse confirm(Long id) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EntityNotFoundException("Don en ligne introuvable : " + id));
        if (transaction.isOnline()) {
            throw new BusinessRuleViolationException("DON-07",
                "Ce don est payé en ligne : son statut est confirmé automatiquement par le prestataire de paiement. "
                    + "Utilisez « Vérifier le statut » si la confirmation tarde.");
        }
        requireStatus(transaction, PaymentTransactionStatus.PENDING);

        Contribution contribution = createContribution(transaction, "Don en ligne confirmé — référence " + transaction.getReference());
        transaction.setStatus(PaymentTransactionStatus.CONFIRMED);
        transaction.setConfirmedAt(Instant.now());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        auditLogService.record("PAYMENT_TRANSACTION", saved.getId(), "CONFIRM",
            "Don en ligne " + saved.getReference() + " confirmé (" + saved.getAmount() + " FCFA pour " + envelopeName(saved)
                + "), contribution #" + contribution.getId() + " créée");
        return PaymentTransactionResponse.from(saved);
    }

    /**
     * Annulation : toujours possible en mode manuel (PENDING) ; en mode en ligne, uniquement sur un
     * don PROCESSING signalé « à vérifier » (la réconciliation automatique a cessé) — jamais sur un
     * don dont PawaPay pourrait encore confirmer le paiement dans les secondes qui suivent.
     */
    public PaymentTransactionResponse cancel(Long id, CancelRequest request) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EntityNotFoundException("Don en ligne introuvable : " + id));
        if (transaction.isOnline()) {
            if (transaction.getStatus() != PaymentTransactionStatus.PROCESSING || !transaction.isNeedsReview()) {
                throw new BusinessRuleViolationException("DON-08",
                    "Un don payé en ligne ne peut être abandonné que s'il est resté sans réponse du prestataire "
                        + "au-delà du délai de réconciliation (statut « à vérifier »).");
            }
        } else {
            requireStatus(transaction, PaymentTransactionStatus.PENDING);
        }

        transaction.setStatus(PaymentTransactionStatus.CANCELLED);
        transaction.setCancelReason(request.reason());
        transaction.setNextCheckAt(null);
        transaction.setNeedsReview(false);
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        auditLogService.record("PAYMENT_TRANSACTION", saved.getId(), "CANCEL",
            "Don en ligne " + saved.getReference() + " annulé : " + request.reason());
        return PaymentTransactionResponse.from(saved);
    }

    /**
     * Applique, sous verrou de ligne, ce que le prestataire a rapporté pour un don en ligne.
     * Idempotent : rejouer le même résultat (callback dupliqué, polling concurrent) ne change rien
     * une fois le don final. Appelé par PawaPayReconciliationService, jamais directement par un
     * contrôleur.
     *
     * @return la transaction mise à jour, ou vide si elle n'existe pas
     */
    public Optional<PaymentTransaction> applyProviderOutcome(Long id, DepositOutcome outcome) {
        Optional<PaymentTransaction> found = paymentTransactionRepository.findByIdForUpdate(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PaymentTransaction tx = found.get();
        if (!tx.isOnline()) {
            return Optional.of(tx);
        }

        if (tx.getStatus().isFinal()) {
            // Cas limite : annulé à la main (après « à vérifier ») puis complété chez PawaPay. L'argent a
            // bien été reçu : on crée la Contribution, on confirme et on signale l'anomalie au
            // back-office. Toute autre information sur un don final est ignorée (idempotence).
            if (tx.getStatus() == PaymentTransactionStatus.CANCELLED && outcome.kind() == DepositOutcome.Kind.COMPLETED
                && tx.getContribution() == null) {
                Contribution contribution = createContribution(tx,
                    "Don en ligne reçu via PawaPay après annulation locale — référence " + tx.getReference());
                tx.setStatus(PaymentTransactionStatus.CONFIRMED);
                tx.setConfirmedAt(Instant.now());
                tx.setProviderStatus(outcome.providerStatus());
                tx.setProviderReference(outcome.providerTransactionId() != null ? outcome.providerTransactionId() : tx.getProviderReference());
                tx.setNeedsReview(true);
                paymentTransactionRepository.save(tx);
                auditLogService.record("PAYMENT_TRANSACTION", tx.getId(), "ANOMALY",
                    "Don en ligne " + tx.getReference() + " annulé localement (" + tx.getCancelReason() + ") mais COMPLETED chez PawaPay : "
                        + "contribution #" + contribution.getId() + " créée, à vérifier");
                log.warn("Anomalie PawaPay : don {} annulé puis complété, contribution #{} créée", tx.getReference(), contribution.getId());
            }
            return Optional.of(tx);
        }

        switch (outcome.kind()) {
            case COMPLETED -> {
                Contribution contribution = createContribution(tx, "Don en ligne payé via PawaPay — référence " + tx.getReference());
                tx.setStatus(PaymentTransactionStatus.CONFIRMED);
                tx.setConfirmedAt(Instant.now());
                tx.setProviderStatus(outcome.providerStatus());
                if (outcome.providerTransactionId() != null) {
                    tx.setProviderReference(outcome.providerTransactionId());
                }
                tx.setNextCheckAt(null);
                tx.setNeedsReview(false);
                auditLogService.record("PAYMENT_TRANSACTION", tx.getId(), "AUTO_CONFIRM",
                    "Don en ligne " + tx.getReference() + " payé via PawaPay (" + tx.getAmount() + " FCFA pour " + envelopeName(tx)
                        + "), contribution #" + contribution.getId() + " créée");
            }
            case FAILED, NOT_FOUND -> {
                tx.setStatus(PaymentTransactionStatus.FAILED);
                tx.setProviderStatus(outcome.providerStatus());
                tx.setFailureCode(outcome.failureCode());
                tx.setFailureMessage(outcome.failureMessage());
                tx.setNextCheckAt(null);
                tx.setNeedsReview(false);
                auditLogService.record("PAYMENT_TRANSACTION", tx.getId(), "FAILED",
                    "Don en ligne " + tx.getReference() + " échoué chez PawaPay : " + outcome.failureCode()
                        + (outcome.failureMessage() != null ? " — " + outcome.failureMessage() : ""));
            }
            case PENDING, UNAVAILABLE -> {
                if (outcome.providerStatus() != null) {
                    tx.setProviderStatus(outcome.providerStatus());
                }
                scheduleNextCheck(tx);
            }
        }
        return Optional.of(paymentTransactionRepository.save(tx));
    }

    /**
     * Prochaine relecture avec recul exponentiel (15 s, 30 s, 1 min... plafonné à 5 min). Au-delà
     * du délai de réconciliation, on cesse d'interroger automatiquement et on signale le don « à
     * vérifier » — sans jamais le déclarer échoué nous-mêmes : seul PawaPay peut le dire.
     */
    private void scheduleNextCheck(PaymentTransaction tx) {
        tx.setCheckCount(tx.getCheckCount() + 1);
        Instant deadline = tx.getCreatedAt().plus(Duration.ofMinutes(pawaPayProperties.getPendingTimeoutMinutes()));
        if (Instant.now().isAfter(deadline)) {
            if (!tx.isNeedsReview()) {
                tx.setNeedsReview(true);
                auditLogService.record("PAYMENT_TRANSACTION", tx.getId(), "REVIEW_NEEDED",
                    "Don en ligne " + tx.getReference() + " toujours sans statut final chez PawaPay après "
                        + pawaPayProperties.getPendingTimeoutMinutes() + " min : à vérifier manuellement");
            }
            tx.setNextCheckAt(null);
            return;
        }
        long multiplier = 1L << Math.min(tx.getCheckCount(), 10);
        Duration delay = FIRST_CHECK_DELAY.multipliedBy(multiplier);
        if (delay.compareTo(MAX_CHECK_DELAY) > 0) {
            delay = MAX_CHECK_DELAY;
        }
        tx.setNextCheckAt(Instant.now().plus(delay));
    }

    private Contribution createContribution(PaymentTransaction transaction, String notes) {
        LocalDate today = LocalDate.now();
        Contribution contribution = Contribution.builder()
            .project(transaction.getProject())
            .category(transaction.getCategory())
            .donor(resolveDonor(transaction))
            .paymentChannel(transaction.getPaymentChannel())
            .amount(transaction.getAmount())
            .periodMonth(today.withDayOfMonth(1))
            .transactionDate(today)
            .movementType(MovementType.STANDARD)
            .nature(MovementNature.DONATION)
            .anonymous(transaction.isAnonymous())
            .notes(notes)
            .build();
        Contribution saved = contributionRepository.save(contribution);
        transaction.setContribution(saved);
        return saved;
    }

    /**
     * Rattache la contribution à un Donor, en réutilisant un donateur existant lorsque le contact
     * fourni (email ou téléphone) correspond exactement à un donateur déjà connu — jamais sur la
     * seule base du nom, trop ambigu (voir DonorRepository). En mode en ligne, le numéro payeur
     * sert de contact de repli. Sans nom ni contact (don anonyme), aucun Donor n'est créé.
     */
    private Donor resolveDonor(PaymentTransaction transaction) {
        String name = transaction.getDonorName();
        String contact = transaction.getDonorContact();
        if ((contact == null || contact.isBlank()) && !transaction.isAnonymous()) {
            contact = transaction.getPayerPhone();
        }
        boolean hasName = name != null && !name.isBlank();
        boolean hasContact = contact != null && !contact.isBlank();
        if (!hasName && !hasContact) {
            return null;
        }

        if (hasContact) {
            String trimmed = contact.trim();
            Optional<Donor> existing = trimmed.contains("@")
                ? donorRepository.findByEmailIgnoreCase(trimmed)
                : donorRepository.findByPhone(trimmed);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Donor donor = Donor.builder()
            .fullName(hasName ? name.trim() : "Donateur")
            .email(hasContact && contact.contains("@") ? contact.trim() : null)
            .phone(hasContact && !contact.contains("@") ? contact.trim() : null)
            .build();
        return donorRepository.save(donor);
    }

    private static String envelopeName(PaymentTransaction tx) {
        return tx.getProject() != null ? tx.getProject().getName() : tx.getCategory().getName();
    }

    private void requireStatus(PaymentTransaction transaction, PaymentTransactionStatus expected) {
        if (transaction.getStatus() != expected) {
            throw new BusinessRuleViolationException("DON-03",
                "Ce don en ligne a déjà été " + switch (transaction.getStatus()) {
                    case CONFIRMED -> "confirmé";
                    case CANCELLED -> "annulé";
                    case FAILED -> "déclaré échoué par le prestataire";
                    default -> "pris en charge";
                } + ".");
        }
    }

    private PaymentTransaction find(Long id) {
        return paymentTransactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Don en ligne introuvable : " + id));
    }
}
