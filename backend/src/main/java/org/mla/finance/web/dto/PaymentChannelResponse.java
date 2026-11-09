package org.mla.finance.web.dto;

import org.mla.finance.domain.PaymentChannel;

/** RG-15 : canal de paiement complet, pour l'écran d'administration des canaux. */
public record PaymentChannelResponse(Long id, String code, String label, boolean active, int sortOrder) {
    public static PaymentChannelResponse from(PaymentChannel c) {
        return new PaymentChannelResponse(c.getId(), c.getCode(), c.getLabel(), c.isActive(), c.getSortOrder());
    }
}
