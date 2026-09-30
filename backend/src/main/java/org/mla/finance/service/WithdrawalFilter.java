package org.mla.finance.service;

import org.mla.finance.domain.WithdrawalStatus;

import java.time.LocalDate;

/** Critères de recherche optionnels et combinables ; un critère null est ignoré. */
public record WithdrawalFilter(
    Long projectId,
    Long categoryId,
    WithdrawalStatus status,
    LocalDate dateFrom,
    LocalDate dateTo
) {
}
