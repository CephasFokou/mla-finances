package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.MovementNature;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Nouvelle entrée d'argent. {@code nature} vaut CONTRIBUTION si absente ; une contribution mensuelle
 * (MONTHLY_CONTRIBUTION) va toujours dans la caisse principale. {@code activityId} rattache une
 * entrée de la caisse (retour, don) à une activité.
 */
public record ContributionRequest(
    Long projectId,
    Long categoryId,
    Long donorId,
    @NotNull Long paymentChannelId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotNull YearMonth periodMonth,
    @NotNull LocalDate transactionDate,
    boolean anonymous,
    @Size(max = 500) String notes,
    MovementNature nature,
    Long activityId,
    @Size(max = 500) String remark
) {
}
