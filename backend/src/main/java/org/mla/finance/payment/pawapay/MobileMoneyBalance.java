package org.mla.finance.payment.pawapay;

import java.math.BigDecimal;
import java.time.Instant;

/** Solde Mobile Money du projet MLA chez wallet-ledger (GET /api/v1/balance). */
public record MobileMoneyBalance(String currency, BigDecimal available, BigDecimal reserved, Instant asOf) {
}
