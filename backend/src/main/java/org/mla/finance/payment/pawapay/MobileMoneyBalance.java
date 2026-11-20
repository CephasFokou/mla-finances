package org.mla.finance.payment.pawapay;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Solde Mobile Money du projet MLA chez wallet-ledger (GET /api/v1/balance). available : solde moins
 * les sorties en cours ; reserved : ces sorties en cours. openingBalance / openingDate : solde
 * d'ouverture figé à la bascule du projet sur wallet-ledger (null si aucun).
 */
public record MobileMoneyBalance(String currency, BigDecimal available, BigDecimal reserved, Instant asOf,
                                 BigDecimal openingBalance, Instant openingDate) {

    public MobileMoneyBalance(String currency, BigDecimal available, BigDecimal reserved, Instant asOf) {
        this(currency, available, reserved, asOf, null, null);
    }

    /** Σ des mouvements terminés du projet : le disponible plus ce qui est réservé pour les sorties en cours. */
    public BigDecimal settled() {
        return available.add(reserved == null ? BigDecimal.ZERO : reserved);
    }
}
