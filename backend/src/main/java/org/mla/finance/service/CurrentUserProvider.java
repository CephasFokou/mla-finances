package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.User;
import org.mla.finance.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utilisateur authentifié de la requête courante (RG-18 : le sujet du JWT est l'id utilisateur).
 * Vide hors requête authentifiée (démarrage, import, tests sans jeton).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public Optional<User> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(Long.valueOf(jwtAuthentication.getToken().getSubject()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public User currentOrNull() {
        return current().orElse(null);
    }
}
