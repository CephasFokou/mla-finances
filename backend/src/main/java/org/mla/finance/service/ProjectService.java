package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.domain.ProjectReminderAudience;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.reminder.ReminderClock;
import org.mla.finance.reminder.ReminderService;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.web.dto.ProjectRemindersRequest;
import org.mla.finance.web.dto.ProjectCreateRequest;
import org.mla.finance.web.dto.ProjectOccurrenceRequest;
import org.mla.finance.web.dto.ProjectResponse;
import org.mla.finance.web.dto.ProjectUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestion des projets/enveloppes : création, modification descriptive, cycle de vie et occurrences
 * récurrentes (RG-19, Comfrat). Le calcul de solde reste dans BalanceService (RG-07).
 *
 * Cycle de vie retenu (à confirmer avec Cephas si le besoin observé en test diverge) :
 * DRAFT -> ACTIVE -> COMPLETED -> ARCHIVED, avec un raccourci ACTIVE -> ARCHIVED (projet
 * interrompu) et DRAFT -> ARCHIVED (projet abandonné avant lancement). Aucune transition arrière :
 * une fois ARCHIVED, un projet ne peut plus être réactivé (à recréer si besoin). "Statut
 * automatique" n'est pas implémenté ici : la cible atteinte (targetAmount) n'archive/ne complète
 * pas le projet toute seule, elle reste un signal affiché côté back-office (RG à préciser).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;
    private final DonorRepository donorRepository;
    private final ReminderService reminderService;
    private final ReminderClock reminderClock;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(ProjectStatus status) {
        List<Project> projects = status != null
            ? projectRepository.findByStatus(status)
            : projectRepository.findAll(Sort.by("name"));
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return ProjectResponse.from(find(id));
    }

    public ProjectResponse create(ProjectCreateRequest request) {
        Project project = Project.builder()
            .name(request.name())
            .description(request.description())
            .status(ProjectStatus.DRAFT)
            .openingBalance(request.openingBalance())
            .targetAmount(request.targetAmount())
            .recurringSeriesId(request.recurringSeriesId())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .build();
        if (request.reminders() != null) {
            applyReminders(project, request.reminders());
        }
        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = find(id);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("PROJECT_LIFECYCLE",
                "Un projet archivé ne peut plus être modifié.");
        }
        project.setName(request.name());
        project.setDescription(request.description());
        project.setTargetAmount(request.targetAmount());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        if (request.reminders() != null) {
            boolean wasEnabled = project.isRemindersEnabled();
            applyReminders(project, request.reminders());
            if (wasEnabled != project.isRemindersEnabled()) {
                auditLogService.record("PROJECT", id, "REMINDERS", "Relances de la collecte '" + project.getName() + "' "
                    + (project.isRemindersEnabled() ? "activées" : "désactivées"));
            }
            projectRepository.flush();
            reminderService.syncProjectCases(id);
        }
        return ProjectResponse.from(project);
    }

    /**
     * Bloc « Relances » (cadrage Relances §6 bis). Activées : ouverture par défaut = début de la collecte, sinon
     * aujourd'hui ; échéance par défaut = fin de la collecte, obligatoire sinon ; sélection manuelle non vide.
     */
    private void applyReminders(Project project, ProjectRemindersRequest r) {
        String message = r.message() == null || r.message().isBlank() ? null : r.message().trim();
        Set<Long> memberIds = r.memberIds() == null ? Set.of() : new HashSet<>(r.memberIds());
        if (r.enabled()) {
            LocalDate openFrom = r.openFrom() != null ? r.openFrom()
                : project.getStartDate() != null ? project.getStartDate()
                : reminderClock.now().atZone(ReminderClock.ZONE).toLocalDate();
            LocalDate due = r.dueDate() != null ? r.dueDate() : project.getEndDate();
            if (due == null) {
                throw new InvalidRequestException("reminders.dueDate",
                    "Indiquez l'échéance des relances : la collecte n'a pas de date de fin.");
            }
            if (due.isBefore(openFrom)) {
                throw new InvalidRequestException("reminders.dueDate", "L'échéance des relances précède leur ouverture.");
            }
            if (r.audience() == ProjectReminderAudience.SELECTION) {
                if (memberIds.isEmpty()) {
                    throw new InvalidRequestException("reminders.memberIds", "Cochez au moins un membre à relancer.");
                }
                long members = donorRepository.findAllById(memberIds).stream().filter(d -> d.isMember()).count();
                if (members != memberIds.size()) {
                    throw new InvalidRequestException("reminders.memberIds", "La sélection contient des personnes qui ne sont pas membres.");
                }
            }
            project.setReminderOpenFrom(openFrom);
            project.setReminderDueDate(due);
        } else {
            project.setReminderOpenFrom(r.openFrom());
            project.setReminderDueDate(r.dueDate());
        }
        project.setRemindersEnabled(r.enabled());
        project.setReminderAudience(r.audience());
        project.setReminderMessage(message);
        project.getReminderMemberIds().clear();
        if (r.audience() == ProjectReminderAudience.SELECTION) {
            project.getReminderMemberIds().addAll(memberIds);
        }
    }

    public ProjectResponse activate(Long id) {
        Project project = find(id);
        requireStatus(project, ProjectStatus.DRAFT, "activer");
        project.setStatus(ProjectStatus.ACTIVE);
        auditLogService.record("PROJECT", id, "ACTIVATE", "Projet '" + project.getName() + "' activé");
        return ProjectResponse.from(project);
    }

    public ProjectResponse complete(Long id) {
        Project project = find(id);
        requireStatus(project, ProjectStatus.ACTIVE, "clôturer");
        project.setStatus(ProjectStatus.COMPLETED);
        auditLogService.record("PROJECT", id, "COMPLETE", "Projet '" + project.getName() + "' clôturé");
        reminderService.closeProjectCases(id, "Collecte clôturée");
        return ProjectResponse.from(project);
    }

    public ProjectResponse archive(Long id) {
        Project project = find(id);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ConflictException("Le projet " + id + " est déjà archivé.");
        }
        project.setStatus(ProjectStatus.ARCHIVED);
        auditLogService.record("PROJECT", id, "ARCHIVE", "Projet '" + project.getName() + "' archivé");
        reminderService.closeProjectCases(id, "Collecte archivée");
        return ProjectResponse.from(project);
    }

    /** RG-19 : nouvelle occurrence partageant la série récurrente du projet source. */
    public ProjectResponse createOccurrence(Long sourceId, ProjectOccurrenceRequest request) {
        Project source = find(sourceId);
        if (source.getRecurringSeriesId() == null || source.getRecurringSeriesId().isBlank()) {
            throw new BusinessRuleViolationException("RG-19",
                "Le projet '" + source.getName() + "' ne fait pas partie d'une série récurrente "
                    + "(recurringSeriesId non renseigné) : impossible de lui créer une occurrence.");
        }
        Project occurrence = Project.builder()
            .name(request.name())
            .description(request.description())
            .status(ProjectStatus.DRAFT)
            .openingBalance(request.openingBalance())
            .targetAmount(request.targetAmount())
            .recurringSeriesId(source.getRecurringSeriesId())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .build();
        return ProjectResponse.from(projectRepository.save(occurrence));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> occurrencesOfSeries(String seriesId) {
        return projectRepository.findByRecurringSeriesIdOrderByStartDateAsc(seriesId).stream()
            .map(ProjectResponse::from).toList();
    }

    private void requireStatus(Project project, ProjectStatus expected, String action) {
        if (project.getStatus() != expected) {
            throw new ConflictException(
                "Impossible de " + action + " le projet " + project.getId() + " : statut actuel "
                    + project.getStatus() + " (attendu " + expected + ").");
        }
    }

    private Project find(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + id));
    }
}
