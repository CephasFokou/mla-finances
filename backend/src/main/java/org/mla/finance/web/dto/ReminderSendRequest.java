package org.mla.finance.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Relance d'un membre : contribution mensuelle d'un mois ({@code month} « 2026-11 », mois en cours ou mois impayé de l'année) ou
 * collecte relançable ({@code projectId}). L'un des deux.
 */
public record ReminderSendRequest(
    @NotNull Long donorId,
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mois attendu au format AAAA-MM") String month,
    Long projectId,
    /** Relancer malgré un blocage souple (délai, maximum, ouverture, rappel programmé), tracé dans l'historique. */
    boolean force,
    /** 0 ou absent : envoi immédiat ; sinon relance programmée dans ce nombre de minutes (7 jours au plus). */
    @Min(0) @Max(10080) Integer delayMinutes
) {
    public int delay() {
        return delayMinutes == null ? 0 : delayMinutes;
    }

    @AssertTrue(message = "Indiquez le mois ou la collecte.")
    public boolean isMotiveGiven() {
        return projectId != null || month != null;
    }
}
