package org.mla.finance.service;

import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;

import java.time.LocalDate;
import java.time.YearMonth;

/** Critères de recherche optionnels et combinables ; un critère null est ignoré. */
public record ContributionFilter(
    Long projectId,
    Long categoryId,
    Long donorId,
    Long paymentChannelId,
    MovementType movementType,
    Boolean anonymous,
    YearMonth periodMonth,
    LocalDate dateFrom,
    LocalDate dateTo,
    MovementNature nature,
    Long activityId
) {
    /** Filtre sans nature ni activité (signature historique). */
    public ContributionFilter(Long projectId, Long categoryId, Long donorId, Long paymentChannelId,
                              MovementType movementType, Boolean anonymous, YearMonth periodMonth,
                              LocalDate dateFrom, LocalDate dateTo) {
        this(projectId, categoryId, donorId, paymentChannelId, movementType, anonymous, periodMonth, dateFrom, dateTo, null, null);
    }
}
