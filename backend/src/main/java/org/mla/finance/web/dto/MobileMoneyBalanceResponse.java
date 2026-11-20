package org.mla.finance.web.dto;

import org.mla.finance.payment.fees.MobileMoneyControlService;
import org.mla.finance.payment.pawapay.MobileMoneyBalance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Encart « Solde Mobile Money du MLA » (lecture seule). enabled=false en mode manuel ;
 * reachable=false si wallet-ledger n'a pas répondu (montants alors absents). expected / gap /
 * checkSince : contrôle d'écart avec MLA Finance (voir MobileMoneyControlService), null si indisponible.
 */
public record MobileMoneyBalanceResponse(boolean enabled, boolean reachable, String currency, BigDecimal available,
                                         BigDecimal reserved, Instant asOf, BigDecimal expected, BigDecimal gap,
                                         Instant checkSince) {

    public static MobileMoneyBalanceResponse disabled() {
        return new MobileMoneyBalanceResponse(false, false, null, null, null, null, null, null, null);
    }

    public static MobileMoneyBalanceResponse unreachable() {
        return new MobileMoneyBalanceResponse(true, false, null, null, null, null, null, null, null);
    }

    public static MobileMoneyBalanceResponse from(MobileMoneyBalance b) {
        return from(b, new MobileMoneyControlService.Control(null, null, null));
    }

    public static MobileMoneyBalanceResponse from(MobileMoneyBalance b, MobileMoneyControlService.Control control) {
        return new MobileMoneyBalanceResponse(true, true, b.currency(), b.available(), b.reserved(), b.asOf(),
            control.expected(), control.gap(), control.since());
    }
}
