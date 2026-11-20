package org.mla.finance.web;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.payment.pawapay.PawaPayReconciliationService;
import org.mla.finance.service.WithdrawalPayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Callback PawaPay / webhook wallet-ledger (URL : {@code <API>/api/v1/webhooks/pawapay/<secret>}),
 * commun aux dons (depositId) et aux décaissements de retraits (payoutId, en-tête
 * {@code X-Ledger-Event: payout}). Modèle de confiance volontairement minimal : le corps n'est
 * JAMAIS cru — on n'en extrait que l'identifiant, puis on relit le statut avec notre clé
 * (PawaPayReconciliationService pour un don, WithdrawalPayoutService pour un payout). Un callback forgé, rejoué ou altéré ne peut donc ni confirmer ni
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
    private final WithdrawalPayoutService payoutService;

    @PostMapping("/{secret}")
    public ResponseEntity<Void> callback(@PathVariable String secret,
        @RequestHeader(value = "X-Ledger-Event", required = false) String event,
        @RequestBody(required = false) JsonNode body) {
        if (!properties.isEnabled() || !properties.hasCallbackSecret() || !constantTimeEquals(secret, properties.getCallbackSecret())) {
            return ResponseEntity.notFound().build();
        }
        // Routage sur l'identifiant présent dans le corps : payoutId (X-Ledger-Event: payout) ou
        // depositId (dons). L'en-tête ne sert qu'à écarter une incohérence ; aucun autre champ n'est lu.
        String payoutId = uuidField(body, "payoutId");
        if (payoutId != null && (event == null || "payout".equalsIgnoreCase(event))) {
            handlePayout(payoutId);
            return ResponseEntity.ok().build();
        }
        String depositId = uuidField(body, "depositId");
        if (depositId != null && (event == null || "deposit".equalsIgnoreCase(event))) {
            handleDeposit(depositId);
            return ResponseEntity.ok().build();
        }
        log.warn("Callback sans depositId ni payoutId exploitable (événement {}), ignoré", event);
        return ResponseEntity.ok().build();
    }

    private void handleDeposit(String depositId) {
        try {
            reconciliationService.reconcileByDepositId(depositId)
                .ifPresentOrElse(
                    tx -> log.info("Callback PawaPay traité : don {} -> {}", tx.getReference(), tx.getStatus()),
                    () -> log.warn("Callback PawaPay pour un depositId inconnu : {}", depositId));
        } catch (RuntimeException e) {
            // Réponse 200 quand même : le polling reprendra ce don ; PawaPay n'a pas besoin d'un détail.
            log.error("Callback PawaPay depositId={} : traitement échoué", depositId, e);
        }
    }

    private void handlePayout(String payoutId) {
        try {
            payoutService.reconcileByPayoutId(payoutId)
                .ifPresentOrElse(
                    w -> log.info("Webhook payout traité : retrait #{} -> payout {}, retrait {}", w.getId(), w.getPayoutStatus(), w.getStatus()),
                    () -> log.warn("Webhook pour un payoutId inconnu (ou remplacé par une nouvelle tentative) : {}", payoutId));
        } catch (RuntimeException e) {
            log.error("Webhook payout payoutId={} : traitement échoué (le polling reprendra)", payoutId, e);
        }
    }

    private static String uuidField(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        String value = body.get(field).asText();
        return isUuid(value) ? value : null;
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
