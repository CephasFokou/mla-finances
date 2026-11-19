package org.mla.finance.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Suivi public d'un don, lu par son jeton opaque (que seul le payeur détient) : le statut, le motif
 * d'échec, et ce qu'il faut pour imprimer le reçu une fois le paiement confirmé. Aucune donnée
 * interne ; le numéro payeur est masqué.
 *
 * @param kind      DONATION (don à une collecte) ou MONTHLY_CONTRIBUTION (caisse du MLA)
 * @param purpose   nom de la collecte, ou de la caisse pour une contribution mensuelle
 * @param months    mois couverts (« AAAA-MM »), vide pour un don
 * @param donorName nom saisi (ou nom usuel du membre), null pour un don anonyme
 * @param payerPhone numéro débité, masqué (« 6** ** 67 89 »), null en paiement manuel
 */
public record PublicDonationStatusResponse(
    String reference,
    String status,
    String failureCode,
    String failureMessage,
    String kind,
    BigDecimal amount,
    String purpose,
    List<String> months,
    String donorName,
    String channel,
    String payerPhone,
    Instant createdAt,
    Instant confirmedAt
) {
}
