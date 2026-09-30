package org.mla.finance.web.dto;

import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record WithdrawalResponse(
    Long id,
    ProjectRef project,
    CategoryRef category,
    BigDecimal amount,
    String reason,
    WithdrawalStatus status,
    Instant requestedAt,
    Instant decidedAt,
    String rejectionReason,
    Instant processedAt,
    String reference
) {
    public static WithdrawalResponse from(Withdrawal w) {
        return new WithdrawalResponse(
            w.getId(),
            ProjectRef.from(w.getProject()),
            CategoryRef.from(w.getCategory()),
            w.getAmount(),
            w.getReason(),
            w.getStatus(),
            w.getRequestedAt(),
            w.getDecidedAt(),
            w.getRejectionReason(),
            w.getProcessedAt(),
            w.getReference()
        );
    }
}
