package org.mla.finance.web.dto;

import org.mla.finance.domain.Permission;
import org.mla.finance.domain.Role;

import java.util.Set;

public record RoleResponse(Long id, String name, String description, Set<Permission> permissions) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.getPermissions());
    }
}
