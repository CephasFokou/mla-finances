package org.mla.finance.web.dto;

import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;

public record MemberResponse(
    Long id,
    String displayName,
    String firstName,
    String lastName,
    String fullName,
    MemberRole memberRole,
    MemberStatus memberStatus,
    String email,
    String phone,
    String remark
) {
    public static MemberResponse from(Donor d) {
        return new MemberResponse(d.getId(), d.getLabel(), d.getFirstName(), d.getLastName(), d.getFullName(),
            d.getMemberRole(), d.getMemberStatus(), d.getEmail(), d.getPhone(), d.getRemark());
    }
}
