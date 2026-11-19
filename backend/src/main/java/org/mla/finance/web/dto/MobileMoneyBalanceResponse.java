package org.mla.finance.web.dto;

import org.mla.finance.payment.pawapay.MobileMoneyBalance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Encart « Solde Mobile Money du MLA » (lecture seule). enabled=false en mode manuel ;
 * reachable=false si wallet-ledger n'a pas répondu (montants alors absents).
 */
public record MobileMoneyBalanceResponse(boolean enabled, boolean reachable, String currency, BigDecimal available,
                                         BigDecimal reserved, Instant asOf) {

    public static MobileMoneyBalanceResponse disabled() {
        return new MobileMoneyBalanceResponse(false, false, null, null, null, null);
    }

    public static MobileMoneyBalanceResponse unreachable() {
        return new MobileMoneyBalanceResponse(true, false, null, null, null, null);
    }

    public static MobileMoneyBalanceResponse from(MobileMoneyBalance b) {
        return new MobileMoneyBalanceResponse(true, true, b.currency(), b.available(), b.reserved(), b.asOf());
    }
}
