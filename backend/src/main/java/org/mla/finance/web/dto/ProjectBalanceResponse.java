package org.mla.finance.web.dto;

import java.math.BigDecimal;

/**
 * RG-07 (solde jamais stocké) : tout recalculé à la volée.
 * currentBalance (solde réel) = openingBalance + totalContributions - totalWithdrawals (retraits
 * PROCESSED uniquement, donc effectivement décaissés).
 * provisionalBalance (solde prévisionnel, "dépenses engagées") = currentBalance - engagedAmount,
 * où engagedAmount est la somme des retraits PENDING/APPROVED pas encore décaissés.
 * deficit signale un solde prévisionnel négatif (alerte de déficit) — voir WithdrawalService
 * pour la décision de ne pas bloquer la création/validation d'un retrait dans ce cas.
 */
public record ProjectBalanceResponse(
    Long projectId,
    BigDecimal openingBalance,
    BigDecimal totalContributions,
    BigDecimal totalWithdrawals,
    BigDecimal currentBalance,
    BigDecimal engagedAmount,
    BigDecimal provisionalBalance,
    boolean deficit,
    long contributionCount,
    long pendingWithdrawalCount
) {
}
