package org.mla.finance.web.dto;

import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WithdrawalResponse(
    Long id,
    ProjectRef project,
    CategoryRef category,
    BigDecimal amount,
    String reason,
    MovementNature nature,
    DonorRef person,
    PaymentChannelRef paymentChannel,
    ActivityRef activity,
    LocalDate operationDate,
    String remark,
    String transferGroup,
    WithdrawalStatus status,
    Instant requestedAt,
    String requestedByName,
    Instant decidedAt,
    String decidedByName,
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
            w.getNature(),
            DonorRef.from(w.getPerson()),
            PaymentChannelRef.from(w.getPaymentChannel()),
            ActivityRef.from(w.getActivity()),
            w.getOperationDate(),
            w.getRemark(),
            w.getTransferGroup(),
            w.getStatus(),
            w.getRequestedAt(),
            w.getRequestedBy() != null ? w.getRequestedBy().getFullName() : null,
            w.getDecidedAt(),
            w.getDecidedBy() != null ? w.getDecidedBy().getFullName() : null,
            w.getRejectionReason(),
            w.getProcessedAt(),
            w.getReference()
        );
    }
}
