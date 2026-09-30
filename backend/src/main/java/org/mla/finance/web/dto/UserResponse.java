package org.mla.finance.web.dto;

import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * invitationToken n'est renvoyé que tant que le compte est PENDING_INVITATION : aucune intégration
 * email (Brevo) n'est encore branchée (voir claude/module-administration-spec.md), donc c'est le
 * seul moyen actuel de récupérer le lien d'invitation à transmettre manuellement.
 */
public record UserResponse(
    Long id,
    String email,
    String fullName,
    UserStatus status,
    List<RoleRef> roles,
    String invitationToken,
    Instant invitationExpiresAt,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getStatus(),
            user.getRoles().stream().map(RoleRef::from).sorted((a, b) -> a.name().compareTo(b.name())).toList(),
            user.getStatus() == UserStatus.PENDING_INVITATION ? user.getInvitationToken() : null,
            user.getInvitationExpiresAt(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
