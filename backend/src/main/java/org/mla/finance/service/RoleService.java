package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Role;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.web.dto.RolePermissionsUpdateRequest;
import org.mla.finance.web.dto.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Les 4 rôles métier (SUPER_ADMIN, TRESORIER, OPERATEUR, AUDITEUR) sont fixes, seedés par
 * Liquibase (0002-seed-reference-data.xml) — créer ou supprimer un rôle n'est pas exposé ici (hors
 * périmètre, voir claude/module-administration-spec.md). Seule la matrice de permissions de
 * chaque rôle est modifiable, depuis l'écran Administration → Rôles & permissions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        return RoleResponse.from(find(id));
    }

    public RoleResponse updatePermissions(Long id, RolePermissionsUpdateRequest request) {
        Role role = find(id);
        role.getPermissions().clear();
        role.getPermissions().addAll(request.permissions());
        auditLogService.record("ROLE", id, "UPDATE_PERMISSIONS",
            "Permissions du rôle '" + role.getName() + "' mises à jour (" + request.permissions().size() + " permission(s))");
        return RoleResponse.from(role);
    }

    Role find(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Rôle introuvable : " + id));
    }
}
