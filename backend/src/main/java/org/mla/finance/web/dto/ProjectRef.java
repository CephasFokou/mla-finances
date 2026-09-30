package org.mla.finance.web.dto;

import org.mla.finance.domain.Project;

/** Projet imbriqué dans une réponse (id + libellé d'affichage). */
public record ProjectRef(Long id, String name) {
    public static ProjectRef from(Project p) {
        return p == null ? null : new ProjectRef(p.getId(), p.getName());
    }
}
