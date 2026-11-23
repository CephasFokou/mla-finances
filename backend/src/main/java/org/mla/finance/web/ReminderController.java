package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.ReminderTrigger;
import org.mla.finance.reminder.ReminderService;
import org.mla.finance.reminder.ReminderSettingsService;
import org.mla.finance.web.dto.ReminderBulkRequest;
import org.mla.finance.web.dto.ReminderBulkResponse;
import org.mla.finance.web.dto.ReminderCaseDetailResponse;
import org.mla.finance.web.dto.ReminderCloseForMemberRequest;
import org.mla.finance.web.dto.ReminderCloseRequest;
import org.mla.finance.web.dto.ReminderOverviewResponse;
import org.mla.finance.web.dto.ReminderPreviewResponse;
import org.mla.finance.web.dto.ReminderSendRequest;
import org.mla.finance.web.dto.ReminderSendResponse;
import org.mla.finance.web.dto.ReminderSettingsRequest;
import org.mla.finance.web.dto.ReminderSettingsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module Relances (lot 1 : contribution mensuelle, email). Tout est réservé à SEND_REMINDERS, sauf les réglages :
 * lecture pour tout utilisateur connecté, modification réservée à MANAGE_USERS (comme les autres réglages).
 */
@RestController
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final ReminderSettingsService settingsService;

    @GetMapping("/api/v1/reminders")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderOverviewResponse overview(@RequestParam(required = false) String month,
                                             @RequestParam(required = false) Long projectId) {
        return reminderService.overview(month, projectId);
    }

    /** Motifs disponibles : contribution mensuelle du mois en cours, collectes relançables en cours (§6 bis). */
    @GetMapping("/api/v1/reminders/reasons")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public java.util.List<org.mla.finance.reminder.ReminderService.ReminderReasonOption> reasons() {
        return reminderService.reasons();
    }

    @GetMapping("/api/v1/reminders/preview")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderPreviewResponse preview(@RequestParam Long donorId, @RequestParam(required = false) String month,
                                           @RequestParam(required = false) Long projectId) {
        return reminderService.preview(donorId, month, projectId);
    }

    @GetMapping("/api/v1/reminders/cases/{id}")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderCaseDetailResponse detail(@PathVariable Long id) {
        return reminderService.detail(id);
    }

    @PostMapping("/api/v1/reminders")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderSendResponse send(@Valid @RequestBody ReminderSendRequest request) {
        return reminderService.send(request.donorId(), request.month(), request.projectId(), ReminderTrigger.MANUAL);
    }

    @PostMapping("/api/v1/reminders/bulk")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderBulkResponse bulk(@Valid @RequestBody ReminderBulkRequest request) {
        return reminderService.bulk(request.donorIds(), request.month(), request.projectId());
    }

    @PostMapping("/api/v1/reminders/cases/{id}/close")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderCaseDetailResponse close(@PathVariable Long id, @Valid @RequestBody ReminderCloseRequest request) {
        return reminderService.close(id, request.reason());
    }

    @PostMapping("/api/v1/reminders/close")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderCaseDetailResponse closeForMember(@Valid @RequestBody ReminderCloseForMemberRequest request) {
        return reminderService.closeForMember(request.donorId(), request.month(), request.projectId(), request.reason());
    }

    @PostMapping("/api/v1/reminders/cases/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public ReminderCaseDetailResponse acknowledge(@PathVariable Long id) {
        return reminderService.acknowledge(id);
    }

    /** Badge de la navigation : dossiers avec une réponse à lire ou un paiement déclaré non retrouvé. */
    @GetMapping("/api/v1/reminders/attention")
    @PreAuthorize("hasAuthority('SEND_REMINDERS')")
    public java.util.Map<String, Long> attention() {
        return java.util.Map.of("count", reminderService.attentionCount());
    }

    @GetMapping("/api/v1/reminder-settings")
    public ReminderSettingsResponse settings() {
        return settingsService.get();
    }

    @PutMapping("/api/v1/reminder-settings")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ReminderSettingsResponse updateSettings(@Valid @RequestBody ReminderSettingsRequest request) {
        return settingsService.update(request);
    }
}
