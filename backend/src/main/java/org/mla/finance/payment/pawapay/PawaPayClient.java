package org.mla.finance.payment.pawapay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Client HTTP minimal de l'API PawaPay v2 (deux appels : POST /v2/deposits, GET /v2/deposits/{id}),
 * via RestClient comme EmailService — pas de SDK (Maven Central inaccessible depuis l'environnement
 * de développement cloud, voir EmailService). Délais d'attente courts et explicites : un prestataire
 * lent ne doit jamais bloquer un thread web indéfiniment. Ne lève jamais d'exception vers l'appelant
 * pour un problème réseau ou un 5xx : renvoie {@link DepositOutcome#unavailable} et laisse la
 * réconciliation réessayer (PawaPay recommande de vérifier le statut avant de conclure à un échec).
 * Le jeton API n'apparaît jamais dans les logs ; les numéros de téléphone y sont masqués.
 */
@Component
public class PawaPayClient {

    private static final Logger log = LoggerFactory.getLogger(PawaPayClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PawaPayProperties properties;

    public PawaPayClient(PawaPayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + properties.getApiToken())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /** Demande de paiement. depositId est notre UUID : PawaPay ignore tout doublon (DUPLICATE_IGNORED). */
    public DepositOutcome initiateDeposit(String depositId, String amount, String phoneNumber, String provider,
        String clientReferenceId, String customerMessage) {
        Map<String, Object> body = Map.of(
            "depositId", depositId,
            "amount", amount,
            "currency", properties.getCurrency(),
            "payer", Map.of(
                "type", "MMO",
                "accountDetails", Map.of("phoneNumber", phoneNumber, "provider", provider)),
            "clientReferenceId", clientReferenceId,
            "customerMessage", customerMessage
        );
        try {
            return restClient.post()
                .uri("/v2/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode json = readJson(response.getBody());
                    if (status >= 500 || json == null) {
                        log.warn("PawaPay initiation depositId={} : HTTP {} sans réponse exploitable — statut à relire", depositId, status);
                        return DepositOutcome.unavailable("HTTP " + status);
                    }
                    String depositStatus = text(json, "status");
                    if ("ACCEPTED".equals(depositStatus) || "DUPLICATE_IGNORED".equals(depositStatus)) {
                        return DepositOutcome.pending(depositStatus);
                    }
                    JsonNode failure = json.path("failureReason");
                    String code = text(failure, "failureCode");
                    String message = text(failure, "failureMessage");
                    if (status == 401 || status == 403) {
                        // Configuration (jeton, opérateur non activé sur le compte) : à corriger côté MLA,
                        // le donateur n'y peut rien — on le dit tel quel dans les logs, pas à lui.
                        log.error("PawaPay a refusé l'authentification ou l'opérateur ({}) : {} {}", status, code, message);
                    }
                    return DepositOutcome.failed("REJECTED", code != null ? code : "REJECTED",
                        message != null ? message : "Demande de paiement refusée par le prestataire.");
                });
        } catch (RestClientException e) {
            log.warn("PawaPay injoignable à l'initiation depositId={} ({}) — statut à relire", depositId, e.getMessage());
            return DepositOutcome.unavailable(e.getMessage());
        }
    }

    /** Relecture du statut : la seule source de vérité, y compris après réception d'un callback. */
    public DepositOutcome checkDeposit(String depositId) {
        try {
            return restClient.get()
                .uri("/v2/deposits/{id}", depositId)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode json = readJson(response.getBody());
                    if (status != 200 || json == null) {
                        log.warn("PawaPay relecture depositId={} : HTTP {} — nouvelle tentative plus tard", depositId, status);
                        return DepositOutcome.unavailable("HTTP " + status);
                    }
                    if ("NOT_FOUND".equals(text(json, "status"))) {
                        return DepositOutcome.notFound();
                    }
                    JsonNode data = json.has("data") ? json.get("data") : json;
                    String depositStatus = text(data, "status");
                    if (depositStatus == null) {
                        return DepositOutcome.unavailable("réponse sans statut");
                    }
                    return switch (depositStatus) {
                        case "COMPLETED" -> DepositOutcome.completed(depositStatus, text(data, "providerTransactionId"));
                        case "FAILED" -> {
                            JsonNode failure = data.path("failureReason");
                            yield DepositOutcome.failed(depositStatus, text(failure, "failureCode"), text(failure, "failureMessage"));
                        }
                        default -> DepositOutcome.pending(depositStatus);
                    };
                });
        } catch (RestClientException e) {
            log.warn("PawaPay injoignable à la relecture depositId={} ({})", depositId, e.getMessage());
            return DepositOutcome.unavailable(e.getMessage());
        }
    }

    private JsonNode readJson(InputStream body) {
        try {
            byte[] bytes = body.readAllBytes();
            return bytes.length == 0 ? null : objectMapper.readTree(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 2376xxxx1234 -> 2376****1234, pour les logs et le journal d'audit. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
    }
}
