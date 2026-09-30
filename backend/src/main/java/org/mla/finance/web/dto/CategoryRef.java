package org.mla.finance.web.dto;

import org.mla.finance.domain.DonationCategory;

/** Catégorie imbriquée dans une réponse, et payload de GET /categories. */
public record CategoryRef(Long id, String name) {
    public static CategoryRef from(DonationCategory c) {
        return c == null ? null : new CategoryRef(c.getId(), c.getName());
    }
}
