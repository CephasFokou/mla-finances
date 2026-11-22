package org.mla.finance.reminder;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.ReminderSettings;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.repository.ReminderSettingsRepository;
import org.mla.finance.service.AuditLogService;
import org.mla.finance.web.dto.ReminderSettingsRequest;
import org.mla.finance.web.dto.ReminderSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class ReminderSettingsService {

    private final ReminderSettingsRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ReminderSettingsResponse get() {
        return ReminderSettingsResponse.from(load());
    }

    @Transactional
    public ReminderSettingsResponse update(ReminderSettingsRequest request) {
        LocalTime start = LocalTime.parse(request.sendWindowStart());
        LocalTime end = LocalTime.parse(request.sendWindowEnd());
        if (!start.isBefore(end)) {
            throw new InvalidRequestException("sendWindowEnd", "La fin de la plage d'envoi doit suivre son début.");
        }
        ReminderSettings s = load();
        s.setEnabled(request.enabled());
        s.setOpenFromDay(request.openFromDay());
        s.setCooldownNoResponseDays(request.cooldownNoResponseDays());
        s.setCooldownAckDays(request.cooldownAckDays());
        s.setCooldownNotFoundHours(request.cooldownNotFoundHours());
        s.setMaxPerMonth(request.maxPerMonth());
        s.setSendWindowStart(start);
        s.setSendWindowEnd(end);
        s.setAutoReminderOnPromise(request.autoReminderOnPromise());
        ReminderSettings saved = repository.saveAndFlush(s);
        auditLogService.record("REMINDER_SETTINGS", saved.getId(), "REMINDER_SETTINGS_UPDATED",
            "Réglages des relances : " + (saved.isEnabled() ? "activées" : "désactivées") + ", à partir du "
                + saved.getOpenFromDay() + ", " + saved.getMaxPerMonth() + " max/mois, délai sans réponse "
                + saved.getCooldownNoResponseDays() + " j, envois " + start + "-" + end);
        return ReminderSettingsResponse.from(saved);
    }

    private ReminderSettings load() {
        return repository.findById(ReminderSettings.SINGLETON_ID).orElseGet(() -> {
            ReminderSettings s = new ReminderSettings();
            s.setId(ReminderSettings.SINGLETON_ID);
            return s;
        });
    }
}
