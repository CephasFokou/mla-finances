package org.mla.finance.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alimente à la fois l'onglet "Aperçu global" du module Rapports et les indicateurs du Dashboard
 * (remplace les EXAMPLE_KPIS de DashboardPage). globalBalance additionne le solde réel courant de
 * toutes les enveloppes (projets + catégories) — voir ReportsService pour le détail du calcul,
 * cohérent avec BalanceService (RG-07 : jamais stocké, toujours recalculé).
 */
public record ReportsOverviewResponse(
    BigDecimal globalBalance,
    BigDecimal mobilizedThisMonth,
    BigDecimal disbursedThisMonth,
    long pendingWithdrawalsCount,
    Instant generatedAt
) {
}
