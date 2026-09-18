package org.mla.finance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité simplifiée pour le développement local uniquement : tant qu'aucun fournisseur
 * d'identité n'est branché (voir SecurityConfig pour le squelette JWT réel), tous les endpoints
 * sont ouverts pour permettre de développer/tester sans dépendance externe. RG-18 sera appliqué
 * dès que l'authentification réelle sera intégrée. Ce profil ne doit jamais être actif en
 * production (voir application.yml, profil "prod").
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
