package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.AuditLog;
import org.mla.finance.domain.User;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.web.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-17 "audit systématique" : point d'entrée unique pour écrire une ligne du journal d'audit.
 * Appelé depuis les services métier (ContributionService, WithdrawalService, ProjectService,
 * CategoryService, UserService, AuthService) au fil de leurs mutations, dans la même transaction —
 * si la mutation échoue, la ligne d'audit ne l'est pas non plus.
 * <p>
 * L'acteur (actor) est désormais résolu automatiquement depuis le contexte de sécurité (RG-18,
 * authentification réelle) : aucun site d'appel n'a eu besoin d'être modifié pour ça, {@code
 * record(...)} garde exactement la même signature qu'avant. Reste null pour les mutations qui se
 * produisent hors requête HTTP authentifiée (ex. le seed d'administrateur de développement au
 * démarrage — voir security.DevAdminSeeder).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void record(String entityType, Long entityId, String action, String summary) {
        AuditLog log = AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .actor(currentActor())
            .summary(summary)
            .build();
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecifications.matching(filter), pageable)
            .map(AuditLogResponse::from);
    }

    private User currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        try {
            Long userId = Long.valueOf(jwtAuthentication.getToken().getSubject());
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
