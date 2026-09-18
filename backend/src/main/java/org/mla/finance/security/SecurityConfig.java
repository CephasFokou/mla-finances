package org.mla.finance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Squelette de sécurité applicative (hors profil "dev", voir DevSecurityConfig) : API sans état
 * authentifiée par JWT (spring-boot-starter-oauth2-resource-server), CSRF désactivé pour une API
 * REST pure. Le contrôle fin des permissions (RG-18 : permissions toujours vérifiées côté
 * backend, jamais seulement côté client) sera ajouté au niveau service via @PreAuthorize au fil
 * de l'implémentation des modules métier, une fois le fournisseur d'identité choisi et
 * OAUTH2_ISSUER_URI configuré.
 */
@Configuration
@EnableWebSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/api/v1/status"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
