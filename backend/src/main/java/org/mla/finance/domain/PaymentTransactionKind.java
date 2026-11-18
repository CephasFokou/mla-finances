package org.mla.finance.domain;

/**
 * Ce que paie un don public : un don à une collecte (DONATION, project_id) ou une contribution
 * mensuelle d'un membre à la caisse MLA (MONTHLY_CONTRIBUTION, catégorie main_fund, un ou
 * plusieurs mois en un seul paiement).
 */
public enum PaymentTransactionKind {
    DONATION,
    MONTHLY_CONTRIBUTION
}
