package org.mla.finance.web.dto;

import org.mla.finance.domain.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;

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
    Instant confirmedAt
) {
    public static PaymentTransactionResponse from(PaymentTransaction t) {
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
            t.getConfirmedAt()
        );
    }
}
