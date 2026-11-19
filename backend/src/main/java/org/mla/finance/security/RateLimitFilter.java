package org.mla.finance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limitation de débit par adresse IP sur les surfaces exposées sans authentification :
 * <ul>
 *   <li>/api/v1/public/** (site de don : soumission d'un don, polling du statut) ;</li>
 *   <li>/api/v1/auth/** (connexion, mot de passe oublié : freine la force brute et l'énumération) ;</li>
 *   <li>/api/v1/webhooks/** (callbacks PawaPay : chaque appel déclenche une relecture chez PawaPay,
 *       il ne faut pas qu'un tiers puisse nous en faire faire des milliers).</li>
 * </ul>
 * Le reste de l'API est derrière un JWT et n'est pas limité ici.
 * <p>
 * Implémentation « seau à jetons » en mémoire, sans dépendance externe (Bucket4j aurait nécessité
 * une nouvelle dépendance Maven, non vérifiable depuis l'environnement de développement cloud) :
 * un seau par (groupe, IP), rempli à la vitesse de N requêtes/minute, capacité N. Au-delà : 429 +
 * Retry-After. Les seaux inactifs depuis plus de deux minutes sont purgés périodiquement. État
 * local à l'instance : suffisant pour une seule instance (Railway) ; avec plusieurs instances la
 * limite effective serait N × instances, à remplacer alors par un compteur partagé (Redis).
 * <p>
 * Adresse client : CF-Connecting-IP (Cloudflare), à défaut X-Forwarded-For (première adresse),
 * UNIQUEMENT si app.rate-limit.trust-proxy=true (derrière Cloudflare/Railway) — sinon n'importe quel client pourrait se forger une IP différente à
 * chaque requête et contourner la limite. En local, c'est l'adresse TCP réelle.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long IDLE_EVICTION_NANOS = 120_000_000_000L;
    private static final int EVICTION_EVERY_N_REQUESTS = 500;

    private final RateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || group(request.getRequestURI()) == null
            || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String group = group(request.getRequestURI());
        int limit = switch (group) {
            case "auth" -> properties.getAuthRequestsPerMinute();
            case "webhook" -> properties.getWebhookRequestsPerMinute();
            default -> properties.getPublicRequestsPerMinute();
        };
        if (limit <= 0) {
            chain.doFilter(request, response);
            return;
        }
        String key = group + "|" + clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit));
        if (!bucket.tryConsume(limit)) {
            log.warn("Rate limit atteint ({} req/min) pour {} sur {}", limit, clientIp(request), request.getRequestURI());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(bucket.secondsUntilNextToken(limit)));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Trop de requêtes. Réessayez dans quelques instants.\"}");
            return;
        }
        maybeEvict();
        chain.doFilter(request, response);
    }

    static String group(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("/api/v1/public/")) {
            return "public";
        }
        if (uri.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (uri.startsWith("/api/v1/webhooks/")) {
            return "webhook";
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.isTrustProxy()) {
            // Derrière Cloudflare (api-mla.itc-services.xyz, nuage orange) : CF-Connecting-IP est
            // posé par Cloudflare lui-même, alors que la première valeur de X-Forwarded-For peut
            // venir du client (Cloudflare ajoute l'adresse réelle à la suite, sans l'écraser).
            String cloudflare = request.getHeader("CF-Connecting-IP");
            if (cloudflare != null && !cloudflare.isBlank()) {
                return cloudflare.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void maybeEvict() {
        if (requestCounter.incrementAndGet() % EVICTION_EVERY_N_REQUESTS != 0) {
            return;
        }
        long now = System.nanoTime();
        buckets.entrySet().removeIf(e -> now - e.getValue().lastAccessNanos > IDLE_EVICTION_NANOS);
    }

    /** Seau à jetons : capacité = limite, remplissage continu de `limite` jetons par minute. */
    static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        volatile long lastAccessNanos;

        Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastAccessNanos = lastRefillNanos;
        }

        synchronized boolean tryConsume(int perMinute) {
            refill(perMinute);
            lastAccessNanos = System.nanoTime();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized long secondsUntilNextToken(int perMinute) {
            refill(perMinute);
            if (tokens >= 1) {
                return 1;
            }
            double secondsPerToken = 60.0 / perMinute;
            return Math.max(1, (long) Math.ceil((1 - tokens) * secondsPerToken));
        }

        private void refill(int perMinute) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(perMinute, tokens + elapsedSeconds * perMinute / 60.0);
            lastRefillNanos = now;
        }
    }
}
