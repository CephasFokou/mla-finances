package org.mla.finance.reminder.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi de messages WhatsApp via Kapso : {@code POST {base}/meta/whatsapp/v24.0/{phone_number_id}/messages}, en-tête
 * X-API-Key, charge utile standard de l'API Cloud de Meta. Ne lève jamais d'exception : le résultat dit si le message
 * est parti (wamid) ou pourquoi il a échoué. Aucun numéro complet dans les journaux.
 */
@Component
public class KapsoClient {

    private static final Logger log = LoggerFactory.getLogger(KapsoClient.class);

    private final KapsoProperties properties;
    private final RestClient restClient;

    public KapsoClient(KapsoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record Result(boolean sent, String messageId, String error) {
    }

    /** Modèle approuvé : utilisable à tout moment, même hors de la fenêtre de 24 h. */
    public Result sendTemplate(String to, WhatsAppTemplate template) {
        List<Map<String, Object>> components = new ArrayList<>();
        if (!template.bodyParams().isEmpty()) {
            List<Map<String, Object>> params = new ArrayList<>();
            template.bodyParams().forEach((name, value) -> params.add(Map.of(
                "type", "text", "parameter_name", name, "text", sanitizeParam(value))));
            components.add(Map.of("type", "body", "parameters", params));
        }
        int index = 0;
        for (String payload : template.quickReplyPayloads()) {
            components.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(index++),
                "parameters", List.of(Map.of("type", "payload", "payload", payload))));
        }
        if (template.urlSuffix() != null) {
            components.add(Map.of("type", "button", "sub_type", "url", "index", String.valueOf(index),
                "parameters", List.of(Map.of("type", "text", "text", template.urlSuffix()))));
        }
        Map<String, Object> body = base(to, "template");
        body.put("template", Map.of(
            "name", template.name(),
            "language", Map.of("code", properties.getLanguage()),
            "components", components));
        return post(body);
    }

    /** Texte libre : seulement dans les 24 h qui suivent un message du membre. */
    public Result sendText(String to, String text) {
        Map<String, Object> body = base(to, "text");
        body.put("text", Map.of("body", text, "preview_url", false));
        return post(body);
    }

    /** Liste interactive (choix de la date) : seulement dans les 24 h qui suivent un message du membre. */
    public Result sendList(String to, String text, String buttonLabel, List<ListRow> rows) {
        List<Map<String, Object>> rowMaps = rows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.id());
            row.put("title", r.title());
            if (r.description() != null) {
                row.put("description", r.description());
            }
            return row;
        }).toList();
        Map<String, Object> body = base(to, "interactive");
        body.put("interactive", Map.of(
            "type", "list",
            "body", Map.of("text", text),
            "action", Map.of("button", buttonLabel, "sections", List.of(Map.of("title", "Date de versement", "rows", rowMaps)))));
        return post(body);
    }

    /** Ligne d'une liste interactive : titre de 24 caractères au plus, description de 72 au plus. */
    public record ListRow(String id, String title, String description) {
    }

    private Map<String, Object> base(String to, String type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", type);
        return body;
    }

    private Result post(Map<String, Object> body) {
        if (!properties.isReady()) {
            return new Result(false, null, "WhatsApp non configuré (KAPSO_ENABLED, KAPSO_API_KEY, KAPSO_PHONE_NUMBER_ID).");
        }
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/meta/whatsapp/v24.0/" + properties.getPhoneNumberId() + "/messages";
        try {
            Map<?, ?> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", properties.getApiKey())
                .body(body)
                .retrieve()
                .body(Map.class);
            Object messages = response == null ? null : response.get("messages");
            if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first && first.get("id") != null) {
                return new Result(true, first.get("id").toString(), null);
            }
            return new Result(false, null, "Réponse Kapso sans identifiant de message.");
        } catch (RestClientResponseException e) {
            log.warn("Kapso : envoi WhatsApp refusé (HTTP {})", e.getStatusCode().value());
            return new Result(false, null, abbreviate("Refus Kapso (HTTP " + e.getStatusCode().value() + ") : " + e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            log.warn("Kapso : envoi WhatsApp impossible ({})", e.getClass().getSimpleName());
            return new Result(false, null, "Kapso injoignable : " + abbreviate(e.getMessage()));
        }
    }

    /** Meta refuse les paramètres avec retours à la ligne, tabulations ou plus de 4 espaces consécutifs. */
    static String sanitizeParam(String value) {
        String s = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return s.isEmpty() ? "-" : s;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "erreur inconnue";
        }
        return s.length() > 250 ? s.substring(0, 250) + "…" : s;
    }
}
