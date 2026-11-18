package org.mla.finance.web.dto;

import org.mla.finance.domain.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Vue back-office d'une intention de don public, en attente ou déjà traitée. */
public record PaymentTransactionResponse(
    Long id,
    String reference,
    ProjectRef project,
    CategoryRef category,
    PaymentChannelRef paymentChannel,
    BigDecimal amount,
    String donorName,
    String donorContact,
    boolean anonymous,
    String provider,
    String status,
    Long contributionId,
    String cancelReason,
    Instant createdAt,
    Instant confirmedAt,
    boolean online,
    String payerPhone,
    String providerStatus,
    String failureCode,
    String failureMessage,
    boolean needsReview,
    Instant nextCheckAt,
    Instant updatedAt,
    /** DONATION (collecte) ou MONTHLY_CONTRIBUTION (caisse MLA). */
    String kind,
    /** Membre qui paie (contribution mensuelle). */
    DonorRef member,
    /** Mois couverts « YYYY-MM » (contribution mensuelle). */
    List<String> months,
    BigDecimal amountPerMonth,
    /** Mois déjà payés par ce membre en dehors de ce don : avertissement, le secrétariat corrige si besoin. */
    List<String> alreadyPaidMonths
) {
    public static PaymentTransactionResponse from(PaymentTransaction t) {
        return from(t, List.of());
    }

    public static PaymentTransactionResponse from(PaymentTransaction t, List<LocalDate> alreadyPaidMonths) {
        return new PaymentTransactionResponse(
            t.getId(),
            t.getReference(),
            ProjectRef.from(t.getProject()),
            CategoryRef.from(t.getCategory()),
            PaymentChannelRef.from(t.getPaymentChannel()),
            t.getAmount(),
            t.getDonorName(),
            t.getDonorContact(),
            t.isAnonymous(),
            t.getProvider(),
            t.getStatus().name(),
            t.getContribution() != null ? t.getContribution().getId() : null,
            t.getCancelReason(),
            t.getCreatedAt(),
            t.getConfirmedAt(),
            t.isOnline(),
            t.getPayerPhone(),
            t.getProviderStatus(),
            t.getFailureCode(),
            t.getFailureMessage(),
            t.isNeedsReview(),
            t.getNextCheckAt(),
            t.getUpdatedAt(),
            t.getKind().name(),
            DonorRef.from(t.getDonor()),
            t.getCoveredMonths().stream().map(m -> YearMonth.from(m).toString()).toList(),
            t.getAmountPerMonth(),
            alreadyPaidMonths.stream().sorted().map(m -> YearMonth.from(m).toString()).toList()
        );
    }
}
