package org.mla.finance.web.dto;

import org.mla.finance.domain.Activity;

/** Activité imbriquée dans une réponse (id + libellé). */
public record ActivityRef(Long id, String name) {
    public static ActivityRef from(Activity a) {
        return a == null ? null : new ActivityRef(a.getId(), a.getName());
    }
}
