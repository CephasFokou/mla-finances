package org.mla.finance.reminder;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.ReminderSettings;
import org.mla.finance.repository.ReminderSettingsRepository;
import org.mla.finance.service.AuditLogService;
import org.mla.finance.web.dto.ReminderSettingsRequest;
import org.mla.finance.web.dto.ReminderSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
        ReminderSettings s = load();
        s.setEnabled(request.enabled());
        s.setOpenFromDay(request.openFromDay());
        s.setCooldownNoResponseHours(request.cooldownNoResponseHours());
        s.setCooldownAckHours(request.cooldownAckHours());
        s.setCooldownNotFoundHours(request.cooldownNotFoundHours());
        s.setMaxPerMonth(request.maxPerMonth());
        s.setAutoReminderOnPromise(request.autoReminderOnPromise());
        ReminderSettings saved = repository.saveAndFlush(s);
        auditLogService.record("REMINDER_SETTINGS", saved.getId(), "REMINDER_SETTINGS_UPDATED",
            "Réglages des relances : " + (saved.isEnabled() ? "activées" : "désactivées") + ", à partir du "
                + saved.getOpenFromDay() + ", " + saved.getMaxPerMonth() + " max/mois, délai sans réponse "
                + saved.getCooldownNoResponseHours() + " h, après « Bien reçu » " + saved.getCooldownAckHours()
                + " h, non retrouvé " + saved.getCooldownNotFoundHours() + " h");
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
