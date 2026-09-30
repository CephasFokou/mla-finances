package org.mla.finance.web.dto;

import org.mla.finance.domain.Donor;

/** Donateur imbriqué dans une réponse, et payload de GET /donors. */
public record DonorRef(Long id, String fullName) {
    public static DonorRef from(Donor d) {
        return d == null ? null : new DonorRef(d.getId(), d.getFullName());
    }
}
