package org.mla.finance.web.dto;

import org.mla.finance.domain.ReminderSettings;

import java.time.Instant;

public record ReminderSettingsResponse(
    boolean enabled,
    int openFromDay,
    int cooldownNoResponseHours,
    int cooldownAckHours,
    int cooldownNotFoundHours,
    int maxPerMonth,
    boolean autoReminderOnPromise,
    Instant updatedAt
) {
    public static ReminderSettingsResponse from(ReminderSettings s) {
        return new ReminderSettingsResponse(s.isEnabled(), s.getOpenFromDay(), s.getCooldownNoResponseHours(),
            s.getCooldownAckHours(), s.getCooldownNotFoundHours(), s.getMaxPerMonth(), s.isAutoReminderOnPromise(),
            s.getUpdatedAt());
    }
}
