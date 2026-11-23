package org.mla.finance.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mla.finance.reminder.whatsapp.KapsoProperties;
import org.mla.finance.reminder.whatsapp.KapsoWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook Kapso (WhatsApp) : public (permitAll sur /api/v1/webhooks/**) mais signé. La signature
 * {@code X-Webhook-Signature} = HMAC-SHA256(KAPSO_WEBHOOK_SECRET, corps brut) en hexadécimal est vérifiée sur les
 * octets reçus, avant toute lecture du JSON. Signature absente ou fausse : 401. Kapso désactivé : 404.
 */
@RestController
public class KapsoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KapsoWebhookController.class);

    private final KapsoProperties properties;
    private final KapsoWebhookService service;
    private final ObjectMapper objectMapper;

    public KapsoWebhookController(KapsoProperties properties, KapsoWebhookService service, ObjectMapper objectMapper) {
        this.properties = properties;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/webhooks/kapso")
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
                                        @RequestHeader(value = "X-Webhook-Event", required = false) String event,
                                        @RequestBody(required = false) byte[] body) throws IOException {
        String secret = properties.getWebhookSecret();
        if (!properties.isEnabled() || secret == null || secret.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || !validSignature(secret, body, signature)) {
            log.warn("Webhook Kapso refusé : signature absente ou invalide");
            return ResponseEntity.status(401).build();
        }
        JsonNode root = objectMapper.readTree(body);
        // Livraison groupée possible (buffering) : un tableau, ou { "data": [...] }.
        JsonNode items = root.isArray() ? root : root.path("data").isArray() ? root.path("data") : null;
        if (items != null) {
            for (JsonNode item : items) {
                service.handle(event, item);
            }
        } else {
            service.handle(event, root);
        }
        return ResponseEntity.ok().build();
    }

    static boolean validSignature(String secret, byte[] body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
            String given = signature.trim().toLowerCase().replaceFirst("^sha256=", "");
            return MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
