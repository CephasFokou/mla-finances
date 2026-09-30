package org.mla.finance.web.dto;

import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Contrat JSON normatif (docs/module-contributions-spec.md §7) : objets imbriqués, null si non applicable. */
public record ContributionResponse(
    Long id,
    ProjectRef project,
    CategoryRef category,
    DonorRef donor,
    PaymentChannelRef paymentChannel,
    BigDecimal amount,
    LocalDate periodMonth,
    LocalDate transactionDate,
    MovementType movementType,
    boolean anonymous,
    String reference,
    String notes,
    Long adjustsContributionId,
    Instant createdAt
) {
    public static ContributionResponse from(Contribution c) {
        return new ContributionResponse(
            c.getId(),
            ProjectRef.from(c.getProject()),
            CategoryRef.from(c.getCategory()),
            DonorRef.from(c.getDonor()),
            PaymentChannelRef.from(c.getPaymentChannel()),
            c.getAmount(),
            c.getPeriodMonth(),
            c.getTransactionDate(),
            c.getMovementType(),
            c.isAnonymous(),
            c.getReference(),
            c.getNotes(),
            c.getAdjustsContribution() != null ? c.getAdjustsContribution().getId() : null,
            c.getCreatedAt()
        );
    }
}
