package org.mla.finance.web.dto;

/** Canal proposé sur le site de don ; online = demande de paiement poussée sur le téléphone (PawaPay). */
public record PublicPaymentChannelResponse(Long id, String code, String label, boolean online) {
}
