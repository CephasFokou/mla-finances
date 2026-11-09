package org.mla.finance.service;

import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.WithdrawalStatus;

import java.time.LocalDate;

/**
 * Critères de recherche optionnels et combinables ; un critère null est ignoré. Les bornes de date
 * portent sur la date réelle de la sortie quand elle est connue (décaissée), sinon sur la date de
 * la demande.
 */
public record WithdrawalFilter(
    Long projectId,
    Long categoryId,
    WithdrawalStatus status,
    LocalDate dateFrom,
    LocalDate dateTo,
    MovementNature nature,
    Long activityId,
    Long personId
) {
    public WithdrawalFilter(Long projectId, Long categoryId, WithdrawalStatus status, LocalDate dateFrom, LocalDate dateTo) {
        this(projectId, categoryId, status, dateFrom, dateTo, null, null, null);
    }
}
