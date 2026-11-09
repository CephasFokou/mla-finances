package org.mla.finance.web.dto;

import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;

/**
 * Personne imbriquée dans une réponse, et payload de GET /donors. {@code fullName} reste le nom
 * complet (compatibilité) ; {@code displayName} est le nom usuel du journal (« Maman Jeanne »).
 */
public record DonorRef(Long id, String fullName, String displayName, MemberRole memberRole) {
    public static DonorRef from(Donor d) {
        return d == null ? null : new DonorRef(d.getId(), d.getFullName(), d.getLabel(), d.getMemberRole());
    }
}
