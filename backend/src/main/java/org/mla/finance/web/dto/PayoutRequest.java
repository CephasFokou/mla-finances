package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/withdrawals/{id}/payout : destinataire choisi dans la liste fermée (PayoutOptionsResponse.Recipient.key). */
public record PayoutRequest(@NotBlank String recipientKey) {
}
