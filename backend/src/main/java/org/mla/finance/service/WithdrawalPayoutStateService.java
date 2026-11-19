package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PayoutStatus;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.payment.pawapay.PayoutOutcome;
import org.mla.finance.payment.payout.PayoutRecipientsProperties.Recipient;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Changements d'état du décaissement Mobile Money d'un retrait, toujours sous verrou de ligne du
 * retrait (FOR UPDATE) : préparation avant l'appel, puis application idempotente de ce que
 * wallet-ledger a répondu ou de ce que la relecture a lu. Même modèle que les dons
 * (PaymentTransactionService.applyProviderOutcome) :
 * <ul>
 *   <li>le payoutId est généré et enregistré AVANT l'appel réseau ; un retrait n'a qu'un payout actif ;</li>
 *   <li>un payout final (COMPLETED, FAILED) n'évolue plus : callback rejoué, polling et bouton
 *       « Vérifier » concurrents s'appliquent l'un après l'autre et les suivants ne font rien ;</li>
 *   <li>COMPLETED fait passer le retrait PROCESSED dans la même transaction SQL ; FAILED le laisse
 *       APPROVED (nouvelle tentative possible, avec un nouveau payoutId).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalPayoutStateService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPayoutStateService.class);
    static final Duration FIRST_CHECK_DELAY = Duration.ofSeconds(15);
    static final Duration MAX_CHECK_DELAY = Duration.ofMinutes(5);
    /**
     * Un NOT_FOUND juste après l'envoi peut venir d'une requête encore en vol chez wallet-ledger
     * (délai d'attente dépassé de notre côté) : on ne conclut à l'échec qu'au-delà de ce délai.
     */
    static final Duration NOT_FOUND_GRACE = Duration.ofMinutes(2);

    private final WithdrawalRepository withdrawalRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final WithdrawalService withdrawalService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final PawaPayProperties pawaPayProperties;

    /** Enregistre un nouveau payout PROCESSING sur un retrait APPROVED sans payout actif ; commit avant l'appel. */
    public Withdrawal prepare(Long withdrawalId, Recipient recipient) {
        Withdrawal w = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new EntityNotFoundException("Retrait introuvable : " + withdrawalId));
        if (w.getStatus() != WithdrawalStatus.APPROVED) {
            throw new ConflictException("Seul un retrait validé peut être décaissé par Mobile Money (retrait "
                + withdrawalId + " : statut " + w.getStatus() + ").");
        }
        if (w.hasActivePayout()) {
            throw new ConflictException("Un décaissement Mobile Money est déjà en cours pour le retrait " + withdrawalId
                + " (" + w.getPayoutStatus() + ").");
        }
        BigDecimal amount = w.getAmount();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleViolationException("PAYOUT_AMOUNT",
                "Le montant d'un décaissement Mobile Money doit être un nombre entier de FCFA.");
        }
        Instant now = Instant.now();
        w.setPayoutId(UUID.randomUUID().toString());
        w.setPayoutPhone(recipient.getMsisdn());
        w.setPayoutProvider(recipient.getProvider());
        w.setPayoutStatus(PayoutStatus.PROCESSING);
        w.setPayoutFailureCode(null);
        w.setPayoutRequestedAt(now);
        w.setPayoutCompletedAt(null);
        w.setPayoutRequestedBy(currentUserProvider.currentOrNull());
        w.setPayoutNextCheckAt(now.plus(FIRST_CHECK_DELAY));
        Withdrawal saved = withdrawalRepository.saveAndFlush(w);
        auditLogService.record("WITHDRAWAL", withdrawalId, "PAYOUT_REQUESTED",
            "Décaissement Mobile Money de " + amount.stripTrailingZeros().toPlainString() + " FCFA demandé vers "
                + recipient.getLabel() + " (" + PawaPayClient.maskPhone(recipient.getMsisdn()) + ", " + recipient.getProvider()
                + ") — payout " + saved.getPayoutId());
        return saved;
    }

    /**
     * Applique le résultat de l'envoi ou d'une relecture au payout payoutId. Sans effet si ce payout
     * n'est plus celui du retrait ou s'il est déjà final.
     */
    public Optional<Withdrawal> apply(String payoutId, PayoutOutcome outcome) {
        Optional<Long> id = withdrawalRepository.findByPayoutId(payoutId).map(Withdrawal::getId);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Withdrawal w = withdrawalRepository.findByIdForUpdate(id.get()).orElseThrow();
        if (!payoutId.equals(w.getPayoutId()) || !w.hasActivePayout()) {
            return Optional.of(w);
        }
        switch (outcome.kind()) {
            case COMPLETED -> complete(w);
            case FAILED -> fail(w, outcome.failureCode(), outcome.failureMessage());
            case NOT_FOUND -> {
                if (Instant.now().isAfter(w.getPayoutRequestedAt().plus(NOT_FOUND_GRACE))) {
                    fail(w, outcome.failureCode(), outcome.failureMessage());
                } else {
                    scheduleNextCheck(w);
                }
            }
            case PENDING_APPROVAL -> {
                if (w.getPayoutStatus() != PayoutStatus.PENDING_APPROVAL) {
                    w.setPayoutStatus(PayoutStatus.PENDING_APPROVAL);
                    auditLogService.record("WITHDRAWAL", w.getId(), "PAYOUT_PENDING_APPROVAL",
                        "Décaissement Mobile Money du retrait #" + w.getId() + " en attente de validation dans la Trésorerie");
                }
                scheduleNextCheck(w);
            }
            case PROCESSING -> {
                // Validé dans la Trésorerie puis envoyé à l'opérateur : de nouveau en cours.
                w.setPayoutStatus(PayoutStatus.PROCESSING);
                scheduleNextCheck(w);
            }
            case UNAVAILABLE -> scheduleNextCheck(w);
        }
        return Optional.of(withdrawalRepository.save(w));
    }

    private void complete(Withdrawal w) {
        Instant now = Instant.now();
        w.setPayoutStatus(PayoutStatus.COMPLETED);
        w.setPayoutCompletedAt(now);
        w.setPayoutNextCheckAt(null);
        w.setPayoutFailureCode(null);
        String amount = w.getAmount().stripTrailingZeros().toPlainString();
        if (w.getStatus() != WithdrawalStatus.APPROVED) {
            // Ne devrait pas arriver (annulation et décaissement manuel sont bloqués pendant le payout) :
            // l'argent est parti, on le dit sans toucher au statut du retrait.
            auditLogService.record("WITHDRAWAL", w.getId(), "PAYOUT_ANOMALY",
                "Payout " + w.getPayoutId() + " COMPLETED alors que le retrait #" + w.getId() + " est " + w.getStatus()
                    + " : " + amount + " FCFA envoyés, à vérifier");
            log.warn("Anomalie payout {} : COMPLETED sur un retrait {}", w.getPayoutId(), w.getStatus());
            return;
        }
        withdrawalService.completeByPayout(w, channelFor(w.getPayoutProvider()));
        auditLogService.record("WITHDRAWAL", w.getId(), "PAYOUT_COMPLETED",
            "Retrait #" + w.getId() + " décaissé par Mobile Money : " + amount + " FCFA envoyés au "
                + PawaPayClient.maskPhone(w.getPayoutPhone()) + " (" + w.getPayoutProvider() + ") — payout " + w.getPayoutId());
        log.info("Payout {} COMPLETED : retrait #{} décaissé", w.getPayoutId(), w.getId());
    }

    private void fail(Withdrawal w, String code, String message) {
        w.setPayoutStatus(PayoutStatus.FAILED);
        w.setPayoutFailureCode(truncate(code != null ? code : "FAILED", 60));
        w.setPayoutNextCheckAt(null);
        auditLogService.record("WITHDRAWAL", w.getId(), "PAYOUT_FAILED",
            "Décaissement Mobile Money du retrait #" + w.getId() + " échoué : " + w.getPayoutFailureCode()
                + (message != null ? " (" + truncate(message, 200) + ")" : "") + " — payout " + w.getPayoutId()
                + ". Le retrait reste validé.");
        log.info("Payout {} FAILED ({}) : retrait #{} reste APPROVED", w.getPayoutId(), w.getPayoutFailureCode(), w.getId());
    }

    /** Recul progressif : un quart du temps écoulé depuis l'envoi, entre 15 s et 5 min. */
    private void scheduleNextCheck(Withdrawal w) {
        Duration elapsed = Duration.between(w.getPayoutRequestedAt(), Instant.now());
        Duration delay = elapsed.dividedBy(4);
        if (delay.compareTo(FIRST_CHECK_DELAY) < 0) {
            delay = FIRST_CHECK_DELAY;
        } else if (delay.compareTo(MAX_CHECK_DELAY) > 0) {
            delay = MAX_CHECK_DELAY;
        }
        w.setPayoutNextCheckAt(Instant.now().plus(delay));
    }

    /** Canal MLA correspondant à l'opérateur (inverse de app.payment.pawapay.providers : ORANGE_CMR -> ORANGE_MONEY). */
    private PaymentChannel channelFor(String provider) {
        return pawaPayProperties.getProviders().entrySet().stream()
            .filter(e -> e.getValue().equals(provider))
            .map(Map.Entry::getKey)
            .findFirst()
            .flatMap(paymentChannelRepository::findByCode)
            .orElse(null);
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
