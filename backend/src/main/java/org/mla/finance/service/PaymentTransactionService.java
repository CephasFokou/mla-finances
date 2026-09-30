package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.PaymentTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Traitement back-office des dons publics en attente (PaymentTransaction) : confirmation après
 * vérification manuelle du paiement réel (crée la Contribution correspondante), ou annulation
 * (paiement jamais reçu, doublon, etc.). Aucune permission dédiée n'existe encore pour ce module :
 * réutilise MANAGE_CONTRIBUTIONS (voir web.PaymentTransactionController), à revoir si un rôle plus
 * spécifique s'avère nécessaire.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ContributionRepository contributionRepository;
    private final DonorRepository donorRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> search(PaymentTransactionStatus status, Pageable pageable) {
        Page<PaymentTransaction> page = status != null
            ? paymentTransactionRepository.findByStatus(status, pageable)
            : paymentTransactionRepository.findAll(pageable);
        return page.map(PaymentTransactionResponse::from);
    }

    public PaymentTransactionResponse confirm(Long id) {
        PaymentTransaction transaction = find(id);
        requirePending(transaction);

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
            .anonymous(transaction.isAnonymous())
            .notes("Don en ligne confirmé — référence " + transaction.getReference())
            .build();
        Contribution savedContribution = contributionRepository.save(contribution);

        transaction.setContribution(savedContribution);
        transaction.setStatus(PaymentTransactionStatus.CONFIRMED);
        transaction.setConfirmedAt(Instant.now());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        String envelope = transaction.getProject() != null ? transaction.getProject().getName() : transaction.getCategory().getName();
        auditLogService.record("PAYMENT_TRANSACTION", saved.getId(), "CONFIRM",
            "Don en ligne " + saved.getReference() + " confirmé (" + saved.getAmount() + " FCFA pour " + envelope
                + "), contribution #" + savedContribution.getId() + " créée");

        return PaymentTransactionResponse.from(saved);
    }

    public PaymentTransactionResponse cancel(Long id, CancelRequest request) {
        PaymentTransaction transaction = find(id);
        requirePending(transaction);

        transaction.setStatus(PaymentTransactionStatus.CANCELLED);
        transaction.setCancelReason(request.reason());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        auditLogService.record("PAYMENT_TRANSACTION", saved.getId(), "CANCEL",
            "Don en ligne " + saved.getReference() + " annulé : " + request.reason());

        return PaymentTransactionResponse.from(saved);
    }

    /**
     * Rattache la contribution créée à la confirmation à un Donor, en réutilisant un donateur
     * existant lorsque le contact fourni (email ou téléphone) correspond exactement à un donateur
     * déjà connu — jamais sur la seule base du nom, trop ambigu (voir DonorRepository). Sans nom ni
     * contact (don anonyme), aucun Donor n'est créé.
     */
    private Donor resolveDonor(PaymentTransaction transaction) {
        String name = transaction.getDonorName();
        String contact = transaction.getDonorContact();
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

    private void requirePending(PaymentTransaction transaction) {
        if (transaction.getStatus() != PaymentTransactionStatus.PENDING) {
            throw new BusinessRuleViolationException("DON-03",
                "Ce don en ligne a déjà été " + (transaction.getStatus() == PaymentTransactionStatus.CONFIRMED ? "confirmé" : "annulé") + ".");
        }
    }

    private PaymentTransaction find(Long id) {
        return paymentTransactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Don en ligne introuvable : " + id));
    }
}
