package org.mla.finance.web.dto;

import org.mla.finance.domain.Permission;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;

import java.util.List;

public record CurrentUserResponse(
    Long id,
    String email,
    String fullName,
    List<String> roles,
    List<String> permissions
) {
    public static CurrentUserResponse from(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
        List<String> permissions = user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::name)
            .distinct()
            .sorted()
            .toList();
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFullName(), roles, permissions);
    }
}
