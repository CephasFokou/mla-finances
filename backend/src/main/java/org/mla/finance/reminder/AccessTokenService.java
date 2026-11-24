package org.mla.finance.reminder;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.AccessToken;
import org.mla.finance.domain.AccessTokenPurpose;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.repository.AccessTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Jetons des liens envoyés aux membres (page de réponse d'un email, bouton « Contribuer maintenant »). 128 bits
 * aléatoires en base64url dans le lien ; seul le hachage SHA-256 est stocké. Valables jusqu'à l'échéance (fin du mois visé ou échéance de la collecte)
 * (heure de Douala). Aucun identifiant interne dans l'URL.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccessTokenRepository repository;
    private final ReminderClock clock;

    @Transactional
    public String issue(AccessTokenPurpose purpose, ReminderCase rc) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Valable jusqu'au dernier jour de relance inclus : échéance de la collecte, ou 31/12 pour un mois de l'année.
        LocalDate due = ReminderMotive.of(rc).lastDay();
        repository.save(AccessToken.builder()
            .tokenHash(hash(raw))
            .purpose(purpose)
            .donor(rc.getDonor())
            .reminderCase(rc)
            .periodMonth(rc.getPeriodMonth())
            .expiresAt(due.plusDays(1).atStartOfDay(ReminderClock.ZONE).toInstant())
            .build());
        return raw;
    }

    /** Jeton valide (bon usage, non expiré) ; vide sinon — même réponse pour un jeton inconnu, expiré ou mal formé. */
    @Transactional(readOnly = true)
    public Optional<AccessToken> resolve(String raw, AccessTokenPurpose purpose) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{16,64}")) {
            return Optional.empty();
        }
        Instant now = clock.now();
        return repository.findByTokenHash(hash(raw))
            .filter(t -> t.getPurpose() == purpose)
            .filter(t -> t.getExpiresAt().isAfter(now));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
