package org.mla.finance.web.dto;

import org.mla.finance.domain.MonthlyContributionSettings;

import java.math.BigDecimal;
import java.time.Instant;

public record MonthlyContributionSettingsResponse(boolean enabled, BigDecimal responsableAmount, BigDecimal memberAmount,
                                                  Instant updatedAt) {
    public static MonthlyContributionSettingsResponse from(MonthlyContributionSettings s) {
        return new MonthlyContributionSettingsResponse(s.isEnabled(), s.getResponsableAmount(), s.getMemberAmount(), s.getUpdatedAt());
    }
}
