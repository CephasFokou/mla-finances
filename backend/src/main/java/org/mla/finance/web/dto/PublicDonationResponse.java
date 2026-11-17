package org.mla.finance.web.dto;

/**
 * Réponse à la soumission d'un don public. mode = ONLINE (demande de paiement envoyée sur le
 * téléphone, suivre via trackingToken) ou MANUAL (payer puis attendre la confirmation du
 * secrétariat). trackingToken est opaque et non devinable : c'est lui, et non la référence, qui
 * donne accès au statut (GET /public/donations/{trackingToken}).
 */
public record PublicDonationResponse(String reference, String status, String mode, String trackingToken, String failureMessage) {
}
