package org.mla.finance.web.dto;

import java.math.BigDecimal;

/** Statistiques agrégées et publiques (aucune donnée individuelle) affichées sur la page de don. */
public record PublicStatsResponse(
    BigDecimal totalRaised,
    long contributionCount,
    long activeProjectCount
) {
}
