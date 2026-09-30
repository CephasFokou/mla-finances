package org.mla.finance.web.dto;

import org.mla.finance.domain.PaymentChannel;

/** Canal imbriqué dans une réponse, et payload de GET /payment-channels. */
public record PaymentChannelRef(Long id, String code, String label) {
    public static PaymentChannelRef from(PaymentChannel c) {
        return new PaymentChannelRef(c.getId(), c.getCode(), c.getLabel());
    }
}
