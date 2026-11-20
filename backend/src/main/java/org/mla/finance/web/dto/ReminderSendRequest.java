package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Relance d'un membre pour la contribution mensuelle d'un mois (« 2026-11 », mois en cours uniquement). */
public record ReminderSendRequest(
    @NotNull Long donorId,
    @NotNull @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mois attendu au format AAAA-MM") String month
) {
}
