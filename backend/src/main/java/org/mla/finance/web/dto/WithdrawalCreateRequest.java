package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.MovementNature;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Demande de retrait (workflow complet) ou, avec {@code operationDate}, enregistrement d'une sortie
 * déjà effectuée (POST /withdrawals/record). {@code nature} vaut EXPENSE si absente.
 */
public record WithdrawalCreateRequest(
    Long projectId,
    Long categoryId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotBlank @Size(max = 500) String reason,
    MovementNature nature,
    Long personId,
    Long paymentChannelId,
    Long activityId,
    @PastOrPresent LocalDate operationDate,
    @Size(max = 500) String remark
) {
}
