package org.mla.finance.web.dto;

import java.util.List;

/**
 * Décaissement Mobile Money disponible ? Et vers quels numéros MLA (liste fermée, numéros masqués) ?
 * enabled=false en mode manuel (PAWAPAY_ENABLED=false) : le back-office n'affiche pas l'action.
 */
public record PayoutOptionsResponse(boolean enabled, List<Recipient> recipients) {

    /** key : identifiant à renvoyer dans PayoutRequest ; provider : ORANGE_CMR / MTN_MOMO_CMR. */
    public record Recipient(String key, String label, String provider, String maskedPhone) {
    }
}
