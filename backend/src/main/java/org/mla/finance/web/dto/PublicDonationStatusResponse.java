package org.mla.finance.web.dto;

/** Suivi public d'un don : uniquement ce que le donateur a besoin de savoir, aucune donnée interne. */
public record PublicDonationStatusResponse(String reference, String status, String failureCode, String failureMessage) {
}
