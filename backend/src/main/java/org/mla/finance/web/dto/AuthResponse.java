package org.mla.finance.web.dto;

import org.mla.finance.domain.User;
import org.mla.finance.service.JwtService;

import java.time.Instant;

/** Réponse commune à login/activate/reset-password : jeton JWT + utilisateur courant, pour permettre au front d'ouvrir la session immédiatement sans appel /me supplémentaire. */
public record AuthResponse(
    String token,
    Instant expiresAt,
    CurrentUserResponse user
) {
    public static AuthResponse of(JwtService.IssuedToken issued, User user) {
        return new AuthResponse(issued.token(), issued.expiresAt(), CurrentUserResponse.from(user));
    }
}
