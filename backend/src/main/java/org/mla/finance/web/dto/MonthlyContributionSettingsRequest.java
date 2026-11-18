package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Réglages du parcours public : montants suggérés entiers (XAF), strictement positifs. */
public record MonthlyContributionSettingsRequest(
    boolean enabled,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 0) BigDecimal responsableAmount,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 0) BigDecimal memberAmount
) {
}
