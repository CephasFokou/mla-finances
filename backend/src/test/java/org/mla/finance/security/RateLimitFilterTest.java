package org.mla.finance.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La force brute sur /auth/login est freinée par IP ; une autre IP n'est pas affectée. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.rate-limit.auth-requests-per-minute=3",
    "app.rate-limit.trust-proxy=true"
})
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;

    @Test
    void loginAttemptsBeyondTheLimitGet429WithRetryAfter() throws Exception {
        String body = "{\"email\":\"nobody@mla-finance.dev\",\"password\":\"wrong\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.7")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.7")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        // Une autre adresse garde son propre quota.
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.8")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Derrière Cloudflare, X-Forwarded-For peut être fourni par le client (Cloudflare y ajoute
     * l'adresse réelle à la suite) : changer sa première valeur ne doit pas remettre le quota à zéro.
     */
    @Test
    void cloudflareConnectingIpWinsOverAForgedForwardedFor() throws Exception {
        String body = "{\"email\":\"nobody@mla-finance.dev\",\"password\":\"wrong\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("CF-Connecting-IP", "198.51.100.20")
                    .header("X-Forwarded-For", "10.0.0." + i + ", 198.51.100.20")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                .header("CF-Connecting-IP", "198.51.100.20")
                .header("X-Forwarded-For", "10.0.0.99, 198.51.100.20")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests());
    }
}
