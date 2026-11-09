package org.mla.finance.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;

/** Création/modification d'une personne : membre du MLA (role renseigné) ou tiers (role null). */
public record MemberRequest(
    @NotBlank @Size(max = 80) String displayName,
    @Size(max = 120) String firstName,
    @Size(max = 120) String lastName,
    MemberRole memberRole,
    MemberStatus memberStatus,
    @Email @Size(max = 180) String email,
    @Size(max = 40) String phone,
    @Size(max = 500) String remark
) {
}
