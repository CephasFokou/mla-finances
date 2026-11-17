package org.mla.finance.web;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.payment.pawapay.PawaPayReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Callback PawaPay (URL à déclarer dans le dashboard : {@code <API>/api/v1/webhooks/pawapay/<secret>}).
 * Modèle de confiance volontairement minimal : le corps du callback n'est JAMAIS cru — on n'en
 * extrait que le depositId, puis on relit le statut chez PawaPay avec notre jeton API
 * (PawaPayReconciliationService). Un callback forgé, rejoué ou altéré ne peut donc ni confirmer ni
 * échouer un don : au pire il déclenche une relecture, elle-même idempotente. Trois protections
 * s'ajoutent : le secret d'URL (comparaison en temps constant, 404 indistinguable d'une route
 * inexistante), la limitation de débit par IP (RateLimitFilter), et l'absence de toute information
 * dans la réponse (toujours 200 vide, même pour un depositId inconnu — pas d'oracle). La
 * vérification des signatures HTTP de PawaPay (RFC 9421) n'est pas implémentée : la relecture
 * directe rend le callback non-authentifié inoffensif, ce qui est plus simple et au moins aussi sûr.
 */
@RestController
@RequestMapping("/api/v1/webhooks/pawapay")
@RequiredArgsConstructor
public class PawaPayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PawaPayWebhookController.class);

    private final PawaPayProperties properties;
    private final PawaPayReconciliationService reconciliationService;

    @PostMapping("/{secret}")
    public ResponseEntity<Void> depositCallback(@PathVariable String secret, @RequestBody(required = false) JsonNode body) {
        if (!properties.isEnabled() || !properties.hasCallbackSecret() || !constantTimeEquals(secret, properties.getCallbackSecret())) {
            return ResponseEntity.notFound().build();
        }
        String depositId = body != null && body.hasNonNull("depositId") ? body.get("depositId").asText() : null;
        if (depositId == null || !isUuid(depositId)) {
            log.warn("Callback PawaPay sans depositId exploitable, ignoré");
            return ResponseEntity.ok().build();
        }
        try {
            reconciliationService.reconcileByDepositId(depositId)
                .ifPresentOrElse(
                    tx -> log.info("Callback PawaPay traité : don {} -> {}", tx.getReference(), tx.getStatus()),
                    () -> log.warn("Callback PawaPay pour un depositId inconnu : {}", depositId));
        } catch (RuntimeException e) {
            // Réponse 200 quand même : le polling reprendra ce don ; PawaPay n'a pas besoin d'un détail.
            log.error("Callback PawaPay depositId={} : traitement échoué", depositId, e);
        }
        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
