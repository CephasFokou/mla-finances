package org.mla.finance.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Réglages des relances (Administration). Délais en heures : valeurs par défaut que l'admin peut passer outre. */
public record ReminderSettingsRequest(
    boolean enabled,
    @Min(1) @Max(28) int openFromDay,
    @Min(0) @Max(744) int cooldownNoResponseHours,
    @Min(0) @Max(744) int cooldownAckHours,
    @Min(0) @Max(744) int cooldownNotFoundHours,
    @Min(1) @Max(10) int maxPerMonth,
    boolean autoReminderOnPromise
) {
}
