package org.mla.finance.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.mla.finance.domain.Permission;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Émission des JWT applicatifs (RG-18) : le backend est son propre fournisseur d'identité — pas
 * d'IdP externe (Keycloak/Auth0/Cognito) — cohérent avec les dépendances déjà présentes dans
 * pom.xml (java-jwt) avant même le début de ce module. La vérification côté serveur se fait via
 * NimbusJwtDecoder à clé partagée (voir security.SecurityConfig), qui relit le même secret que
 * celui utilisé ici pour signer.
 * <p>
 * Les permissions de l'utilisateur (agrégées depuis ses rôles) sont embarquées dans le claim
 * "permissions" : c'est ce claim que SecurityConfig convertit en GrantedAuthority Spring Security,
 * ce qui rend les @PreAuthorize("hasAuthority(...)") déjà posés sur tous les endpoints de mutation
 * réellement actifs.
 */
@Service
public class JwtService {

    private static final String ISSUER = "mla-finance";

    private final Algorithm algorithm;
    private final long expirationMinutes;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationMinutes = expirationMinutes;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        List<String> permissions = user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::name)
            .distinct()
            .toList();
        List<String> roleNames = user.getRoles().stream().map(Role::getName).distinct().toList();

        String token = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(String.valueOf(user.getId()))
            .withClaim("email", user.getEmail())
            .withClaim("fullName", user.getFullName())
            .withClaim("roles", roleNames)
            .withClaim("permissions", permissions)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm);

        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
