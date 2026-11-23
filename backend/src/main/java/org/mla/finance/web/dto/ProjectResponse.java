package org.mla.finance.web.dto;

import org.mla.finance.domain.ProjectReminderAudience;
import java.util.List;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Détail complet d'un projet/enveloppe. Le solde courant n'y figure pas (RG-07, solde jamais
 * stocké) : il se lit séparément via GET /api/v1/projects/{id}/balance. */
public record ProjectResponse(
    Long id,
    String name,
    String description,
    ProjectStatus status,
    BigDecimal openingBalance,
    BigDecimal targetAmount,
    String recurringSeriesId,
    LocalDate startDate,
    LocalDate endDate,
    Instant createdAt,
    Instant updatedAt,
    Reminders reminders
) {
    /** Bloc « Relances » de la collecte (cadrage Relances §6 bis). */
    public record Reminders(boolean enabled, ProjectReminderAudience audience, LocalDate openFrom, LocalDate dueDate,
                            String message, List<Long> memberIds) {
    }

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getStatus(),
            p.getOpeningBalance(),
            p.getTargetAmount(),
            p.getRecurringSeriesId(),
            p.getStartDate(),
            p.getEndDate(),
            p.getCreatedAt(),
            p.getUpdatedAt(),
            new Reminders(p.isRemindersEnabled(), p.getReminderAudience(), p.getReminderOpenFrom(), p.getReminderDueDate(),
                p.getReminderMessage(), p.getReminderMemberIds().stream().sorted().toList())
        );
    }
}
