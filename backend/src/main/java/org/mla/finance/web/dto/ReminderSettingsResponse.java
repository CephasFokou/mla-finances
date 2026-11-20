package org.mla.finance.web.dto;

import org.mla.finance.domain.ReminderSettings;

import java.time.Instant;

public record ReminderSettingsResponse(
    boolean enabled,
    int openFromDay,
    int cooldownNoResponseDays,
    int cooldownAckDays,
    int cooldownNotFoundHours,
    int maxPerMonth,
    String sendWindowStart,
    String sendWindowEnd,
    boolean autoReminderOnPromise,
    Instant updatedAt
) {
    public static ReminderSettingsResponse from(ReminderSettings s) {
        return new ReminderSettingsResponse(s.isEnabled(), s.getOpenFromDay(), s.getCooldownNoResponseDays(),
            s.getCooldownAckDays(), s.getCooldownNotFoundHours(), s.getMaxPerMonth(), s.getSendWindowStart().toString(),
            s.getSendWindowEnd().toString(), s.isAutoReminderOnPromise(), s.getUpdatedAt());
    }
}
