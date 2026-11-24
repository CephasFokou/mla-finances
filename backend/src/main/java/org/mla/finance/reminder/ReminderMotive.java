package org.mla.finance.reminder;

import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.domain.ReminderSettings;
import org.mla.finance.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Motif d'une relance (cadrage Relances §3) : la contribution mensuelle d'un mois, ou une collecte relançable
 * (§6 bis). Tout ce qui dépend du motif passe par ici : clé du dossier, ouverture, échéance, libellés.
 */
public record ReminderMotive(ReminderReason reason, YearMonth month, Project project) {

    public ReminderMotive {
        Objects.requireNonNull(reason);
        if (reason == ReminderReason.MONTHLY_CONTRIBUTION) {
            Objects.requireNonNull(month);
        } else {
            Objects.requireNonNull(project);
        }
    }

    public static ReminderMotive monthly(YearMonth month) {
        return new ReminderMotive(ReminderReason.MONTHLY_CONTRIBUTION, month, null);
    }

    public static ReminderMotive project(Project project) {
        return new ReminderMotive(ReminderReason.PROJECT, null, project);
    }

    public static ReminderMotive of(ReminderCase rc) {
        return rc.getReason() == ReminderReason.PROJECT ? project(rc.getProject()) : monthly(YearMonth.from(rc.getPeriodMonth()));
    }

    public boolean isProject() {
        return reason == ReminderReason.PROJECT;
    }

    public Long projectId() {
        return project == null ? null : project.getId();
    }

    /** Clé d'unicité du dossier avec (membre, motif) : « M:2026-11-01 » ou « P:12 ». */
    public String caseKey() {
        return isProject() ? projectKey(project.getId()) : ReminderCase.monthKey(month.atDay(1));
    }

    public static String projectKey(Long projectId) {
        return "P:" + projectId;
    }

    /** Premier jour où la relance est possible. */
    public LocalDate openFrom(ReminderSettings settings) {
        if (isProject()) {
            return project.getReminderOpenFrom();
        }
        return month.atDay(Math.min(settings.getOpenFromDay(), month.lengthOfMonth()));
    }

    /** Échéance affichée : fin de la collecte, ou dernier jour du mois de la contribution. */
    public LocalDate dueDate() {
        if (isProject()) {
            return project.getReminderDueDate() != null ? project.getReminderDueDate() : project.getEndDate();
        }
        return month.atEndOfMonth();
    }

    /**
     * Dernier jour où relancer et répondre est possible : échéance de la collecte ; pour une contribution mensuelle, le
     * 31 décembre de son année (un mois impayé de l'année reste relançable, cadrage Relances lot 3).
     */
    public LocalDate lastDay() {
        return isProject() ? dueDate() : LocalDate.of(month.getYear(), 12, 31);
    }

    /**
     * Date limite proposée au membre aujourd'hui : échéance de la collecte ; fin du mois de la contribution, ou fin du
     * mois en cours pour un mois passé (arriéré).
     */
    public LocalDate memberDeadline(LocalDate today) {
        if (isProject()) {
            return dueDate();
        }
        YearMonth current = YearMonth.from(today);
        return current.isAfter(month) ? current.atEndOfMonth() : month.atEndOfMonth();
    }

    /** Mois passé (contribution mensuelle encore due) : la relance parle d'un arriéré. */
    public boolean isArrears(LocalDate today) {
        return !isProject() && YearMonth.from(today).isAfter(month);
    }

    /** Échéance dépassée : plus aucune relance, les dossiers ouverts sont clos par le job quotidien. */
    public boolean isOver(LocalDate today) {
        LocalDate last = lastDay();
        return last == null || today.isAfter(last);
    }

    /** Collecte : relances activées et collecte en cours (ACTIVE). Contribution mensuelle : toujours. */
    public boolean isRemindable() {
        return !isProject() || (project.isRemindersEnabled() && project.getStatus() == ProjectStatus.ACTIVE);
    }

    /** « novembre 2026 » ou le nom de la collecte. */
    public String label() {
        return isProject() ? project.getName() : ReminderMessageRenderer.monthLabel(month);
    }

    /** Ce que le membre doit : « votre contribution de novembre » / « votre participation à la collecte X ». */
    public String memberPhrase() {
        return isProject()
            ? "votre participation à la collecte « " + project.getName() + " »"
            : "votre contribution de " + month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH);
    }

    /** Pour l'historique : « la contribution de novembre 2026 » / « la collecte X ». */
    public String auditPhrase() {
        return isProject() ? "la collecte « " + project.getName() + " »" : "la contribution de " + label();
    }

    /** Identifiant du motif pour l'API : « 2026-11 » ou « P12 ». */
    public String code() {
        return isProject() ? "P" + project.getId() : month.toString();
    }

    /** Mois du dossier (contribution mensuelle) ; null pour une collecte. */
    public LocalDate periodMonth() {
        return isProject() ? null : month.atDay(1);
    }

    static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("month", "Mois attendu au format AAAA-MM.");
        }
    }
}
