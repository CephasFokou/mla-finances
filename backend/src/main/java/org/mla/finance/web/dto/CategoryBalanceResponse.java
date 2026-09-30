package org.mla.finance.web.dto;

import java.math.BigDecimal;

/** Même forme que ProjectBalanceResponse ; une catégorie n'a pas de solde d'ouverture (toujours 0). */
public record CategoryBalanceResponse(
    Long categoryId,
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
