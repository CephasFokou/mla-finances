package org.mla.finance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Sécurité applicative RG-18 : API sans état, authentifiée par un JWT que le backend émet
 * lui-même (voir JwtService) et vérifie via une clé partagée (pas de fournisseur d'identité
 * externe, pas de JWKS/discovery — cohérent avec java-jwt déjà présent dans pom.xml). Une seule
 * configuration, quel que soit le profil : Cephas a choisi d'activer la vraie authentification
 * directement en dev aussi (voir claude/module-authentification-spec.md), donc l'ancien
 * DevSecurityConfig ("dev" = tout ouvert) a été retiré plutôt que conservé en option.
 * <p>
 * Le claim JWT "permissions" (peuplé par JwtService à partir des rôles de l'utilisateur) est
 * converti en GrantedAuthority Spring Security sans préfixe, ce qui rend actifs tous les
 * @PreAuthorize("hasAuthority('...')") déjà posés sur les endpoints de mutation de chaque module.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("permissions");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
        JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            // Nécessaire uniquement pour que la console H2 (profil dev) puisse s'afficher dans une
            // frame de même origine ; sans incidence en prod où elle n'est jamais activée.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/media/*/file",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/api/v1/status",
                    "/h2-console/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter)
            ));

        return http.build();
    }

    /**
     * app.cors.allowed-origins accepte une liste séparée par des virgules de PATTERNS (pas
     * seulement des origines exactes) — "http://localhost:*" en dev, pour ne pas casser le CORS à
     * chaque fois que Vite choisit un port différent (5173 déjà occupé -> 5174, etc., observé lors
     * des premiers tests réels). setAllowedOriginPatterns (et non setAllowedOrigins) est ce qui
     * permet le "*" ; Spring renvoie alors l'Origin réellement reçue dans la réponse plutôt qu'une
     * valeur fixe, comportement standard et sans risque ici puisque seul le pattern autorisé
     * (localhost, tous ports, en dev) peut matcher. En prod, FRONTEND_ORIGIN reste une origine
     * exacte, donc le comportement est identique à setAllowedOrigins pour ce cas.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        List<String> patterns = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setExposedHeaders(List.of("Location", "Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
