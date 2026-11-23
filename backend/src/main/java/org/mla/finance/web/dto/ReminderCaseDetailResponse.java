package org.mla.finance.web.dto;

import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderDeliveryStatus;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.domain.ReminderTrigger;

import java.time.Instant;
import java.util.List;

/** Chronologie d'un dossier : relances, envois par canal (destinataire masqué), clôture. */
public record ReminderCaseDetailResponse(
    Long id,
    Long donorId,
    String donorLabel,
    ReminderReason reason,
    String month,
    String monthLabel,
    ReminderCaseStatus status,
    int reminderCount,
    Instant nextAllowedAt,
    Instant createdAt,
    Instant closedAt,
    String closedReason,
    String closedBy,
    Long contributionId,
    boolean needsAttention,
    java.time.LocalDate promisedDate,
    List<Entry> reminders,
    List<Response> responses
) {
    /** Réponse du membre : ce qu'il a envoyé et ce que le système en a conclu. */
    public record Response(ReminderChannel channel, String type, java.time.LocalDate promisedDate, String text,
                           String outcome, Instant receivedAt) {
    }

    public record Entry(
        Long id,
        int sequenceNo,
        ReminderTrigger trigger,
        String kind,
        String sentBy,
        Instant createdAt,
        Instant scheduledFor,
        Instant sentAt,
        Instant cancelledAt,
        String message,
        List<Delivery> deliveries
    ) {
    }

    public record Delivery(ReminderChannel channel, String recipient, ReminderDeliveryStatus status, String error,
                           Instant at) {
    }
}
