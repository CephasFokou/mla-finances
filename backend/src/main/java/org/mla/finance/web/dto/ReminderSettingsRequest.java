package org.mla.finance.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Réglages des relances (Administration). Heures au format HH:mm, heure de Douala. */
public record ReminderSettingsRequest(
    boolean enabled,
    @Min(1) @Max(28) int openFromDay,
    @Min(0) @Max(31) int cooldownNoResponseDays,
    @Min(0) @Max(31) int cooldownAckDays,
    @Min(0) @Max(240) int cooldownNotFoundHours,
    @Min(1) @Max(10) int maxPerMonth,
    @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Heure attendue au format HH:mm") String sendWindowStart,
    @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Heure attendue au format HH:mm") String sendWindowEnd,
    boolean autoReminderOnPromise
) {
}
