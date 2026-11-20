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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Client HTTP minimal de l'API PawaPay v2 telle qu'exposée par wallet-ledger (dépôts : POST /v2/deposits,
 * GET /v2/deposits/{id} ; payouts : POST /v2/payouts, GET /v2/payouts/{id} ; solde : GET /api/v1/balance),
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

    // ------------------------------------------------------------------ payouts (wallet-ledger)

    /**
     * Décaissement Mobile Money : POST /v2/payouts. payoutId est notre UUID, déjà enregistré en base :
     * wallet-ledger répond DUPLICATE_IGNORED à un renvoi. Traduction de la réponse :
     * ACCEPTED / DUPLICATE_IGNORED -> PROCESSING ; PENDING_APPROVAL (au-dessus du seuil du projet) ;
     * REJECTED ou tout 4xx -> FAILED (rien n'a été engagé) ; 5xx, coupure ou corps illisible ->
     * UNAVAILABLE (statut inconnu : la relecture tranchera, jamais un échec d'office).
     */
    public PayoutOutcome initiatePayout(String payoutId, String amount, String phoneNumber, String provider,
        String clientReferenceId, String customerMessage) {
        Map<String, Object> body = Map.of(
            "payoutId", payoutId,
            "amount", amount,
            "currency", properties.getCurrency(),
            "recipient", Map.of(
                "type", "MMO",
                "accountDetails", Map.of("phoneNumber", phoneNumber, "provider", provider)),
            "clientReferenceId", clientReferenceId,
            "customerMessage", customerMessage
        );
        try {
            return restClient.post()
                .uri("/v2/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode json = readJson(response.getBody());
                    if (status >= 500) {
                        log.warn("Payout payoutId={} : HTTP {} — statut à relire", payoutId, status);
                        return PayoutOutcome.unavailable("HTTP " + status);
                    }
                    String payoutStatus = text(json, "status");
                    JsonNode failure = json == null ? null : json.path("failureReason");
                    if (status >= 400) {
                        if (status == 401 || status == 403) {
                            log.error("wallet-ledger a refusé la clé de projet pour le payout {} (HTTP {})", payoutId, status);
                        }
                        String code = text(failure, "failureCode");
                        return PayoutOutcome.failed("REJECTED", code != null ? code : "HTTP_" + status, text(failure, "failureMessage"));
                    }
                    if (payoutStatus == null) {
                        log.warn("Payout payoutId={} : HTTP {} sans statut — à relire", payoutId, status);
                        return PayoutOutcome.unavailable("réponse sans statut");
                    }
                    return switch (payoutStatus) {
                        case "ACCEPTED", "DUPLICATE_IGNORED" -> PayoutOutcome.processing(payoutStatus);
                        case "PENDING_APPROVAL" -> PayoutOutcome.pendingApproval();
                        default -> PayoutOutcome.failed(payoutStatus, text(failure, "failureCode"), text(failure, "failureMessage"));
                    };
                });
        } catch (RestClientException e) {
            // HttpURLConnection lève HttpRetryException sur un 401 en réponse à un POST au lieu de rendre
            // le statut : la clé a été refusée, la demande n'a donc pas été enregistrée => échec franc
            // (sinon le payout resterait PROCESSING indéfiniment et bloquerait le retrait).
            Integer refused = authRefusal(e);
            if (refused != null) {
                log.error("wallet-ledger a refusé la clé de projet pour le payout {} (HTTP {})", payoutId, refused);
                return PayoutOutcome.failed("REJECTED", "HTTP_" + refused, "Clé de projet refusée par wallet-ledger.");
            }
            log.warn("wallet-ledger injoignable à l'envoi du payout {} ({}) — statut à relire", payoutId, e.getMessage());
            return PayoutOutcome.unavailable(e.getMessage());
        }
    }

    /** Relecture d'un payout : seule source de vérité, y compris après un webhook. */
    public PayoutOutcome checkPayout(String payoutId) {
        try {
            return restClient.get()
                .uri("/v2/payouts/{id}", payoutId)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode json = readJson(response.getBody());
                    if (status != 200 || json == null) {
                        log.warn("Relecture payout payoutId={} : HTTP {} — nouvelle tentative plus tard", payoutId, status);
                        return PayoutOutcome.unavailable("HTTP " + status);
                    }
                    if ("NOT_FOUND".equals(text(json, "status"))) {
                        return PayoutOutcome.notFound();
                    }
                    JsonNode data = json.has("data") ? json.get("data") : json;
                    String payoutStatus = text(data, "status");
                    if (payoutStatus == null) {
                        return PayoutOutcome.unavailable("réponse sans statut");
                    }
                    return switch (payoutStatus) {
                        case "COMPLETED" -> PayoutOutcome.completed(text(data, "providerTransactionId"));
                        case "FAILED", "REJECTED" -> {
                            JsonNode failure = data.path("failureReason");
                            yield PayoutOutcome.failed(payoutStatus, text(failure, "failureCode"), text(failure, "failureMessage"));
                        }
                        case "PENDING_APPROVAL" -> PayoutOutcome.pendingApproval();
                        default -> PayoutOutcome.processing(payoutStatus);
                    };
                });
        } catch (RestClientException e) {
            log.warn("wallet-ledger injoignable à la relecture du payout {} ({})", payoutId, e.getMessage());
            return PayoutOutcome.unavailable(e.getMessage());
        }
    }

    /**
     * Solde Mobile Money du projet MLA chez wallet-ledger. Route propre à wallet-ledger
     * (GET /api/v1/balance), pas un chemin PawaPay : vide si le service est injoignable ou répond
     * autre chose qu'un solde.
     */
    public Optional<MobileMoneyBalance> balance() {
        try {
            return restClient.get()
                .uri("/api/v1/balance")
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode json = readJson(response.getBody());
                    if (status != 200 || json == null || text(json, "available") == null) {
                        log.warn("Solde wallet-ledger : HTTP {} sans solde exploitable", status);
                        return Optional.<MobileMoneyBalance>empty();
                    }
                    String asOf = text(json, "asOf");
                    return Optional.of(new MobileMoneyBalance(
                        text(json, "currency"),
                        decimal(text(json, "available")),
                        decimal(text(json, "reserved")),
                        asOf == null ? null : Instant.parse(asOf)));
                });
        } catch (RestClientException | IllegalArgumentException | java.time.format.DateTimeParseException e) {
            log.warn("Solde wallet-ledger indisponible ({})", e.getMessage());
            return Optional.empty();
        }
    }

    private static Integer authRefusal(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.HttpRetryException retry && (retry.responseCode() == 401 || retry.responseCode() == 403)) {
                return retry.responseCode();
            }
        }
        return null;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.trim());
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
