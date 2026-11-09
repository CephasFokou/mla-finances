package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.ActivityStatus;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.ActivityRepository;
import org.mla.finance.web.dto.ActivityRequest;
import org.mla.finance.web.dto.ActivityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Activités payées par la caisse (onglet Activités). Jamais supprimées : historique des dépenses. */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ActivityResponse> list() {
        return activityRepository.findAllByOrderByEventDateDescNameAsc().stream().map(ActivityResponse::from).toList();
    }

    public ActivityResponse create(ActivityRequest request) {
        String name = request.name().trim();
        if (activityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Une activité nommée « " + name + " » existe déjà.");
        }
        Activity saved = activityRepository.save(Activity.builder()
            .name(name)
            .periodLabel(request.periodLabel())
            .eventDate(request.eventDate())
            .status(request.status() != null ? request.status() : ActivityStatus.PLANNED)
            .comment(request.comment())
            .build());
        auditLogService.record("ACTIVITY", saved.getId(), "CREATE", "Activité « " + saved.getName() + " » créée");
        return ActivityResponse.from(saved);
    }

    public ActivityResponse update(Long id, ActivityRequest request) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Activité introuvable : " + id));
        String name = request.name().trim();
        if (activityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Une activité nommée « " + name + " » existe déjà.");
        }
        activity.setName(name);
        activity.setPeriodLabel(request.periodLabel());
        activity.setEventDate(request.eventDate());
        if (request.status() != null) {
            activity.setStatus(request.status());
        }
        activity.setComment(request.comment());
        auditLogService.record("ACTIVITY", id, "UPDATE", "Activité « " + activity.getName() + " » modifiée");
        return ActivityResponse.from(activity);
    }
}
