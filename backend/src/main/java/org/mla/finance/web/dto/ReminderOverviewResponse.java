package org.mla.finance.web.dto;

import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderDeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Écran Relances : un mois, ses indicateurs et une ligne par membre actif. */
public record ReminderOverviewResponse(
    String month,
    String monthLabel,
    boolean currentMonth,
    boolean enabled,
    LocalDate openFrom,
    LocalDate dueDate,
    int maxReminders,
    Summary summary,
    List<Row> rows
) {

    public record Summary(int members, int upToDate, int unpaid, int inProgress, int remindable, int remindersSent,
                          int maxReached) {
    }

    /**
     * {@code monthStatus} : PAID, UNPAID ou IN_PROGRESS (paiement en cours de validation). {@code blockedCode} /
     * {@code blockedReason} : pourquoi le bouton « Relancer » est grisé (null si {@code canRemind}).
     */
    public record Row(
        Long donorId,
        String label,
        MemberRole role,
        BigDecimal expectedAmount,
        String monthStatus,
        boolean hasEmail,
        boolean hasPhone,
        Long caseId,
        ReminderCaseStatus caseStatus,
        int reminderCount,
        Instant lastReminderAt,
        Instant lastReminderScheduledFor,
        List<DeliverySummary> lastDeliveries,
        Instant nextAllowedAt,
        boolean canRemind,
        String blockedCode,
        String blockedReason,
        boolean needsAttention
    ) {
    }

    public record DeliverySummary(ReminderChannel channel, ReminderDeliveryStatus status) {
    }
}
