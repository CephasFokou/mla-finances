package org.mla.finance.web.dto;

import org.mla.finance.domain.Role;

/** Rôle imbriqué dans une réponse (id + nom, sans le détail des permissions). */
public record RoleRef(Long id, String name) {
    public static RoleRef from(Role r) {
        return r == null ? null : new RoleRef(r.getId(), r.getName());
    }
}
