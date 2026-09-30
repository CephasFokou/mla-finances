package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotNull;
import org.mla.finance.domain.Permission;

import java.util.Set;

public record RolePermissionsUpdateRequest(@NotNull Set<Permission> permissions) {
}
