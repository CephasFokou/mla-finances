package org.mla.finance.service;

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
    LocalDate dateTo
) {
}
