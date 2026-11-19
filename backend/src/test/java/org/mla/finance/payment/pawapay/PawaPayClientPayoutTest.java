package org.mla.finance.payment.pawapay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client payouts contre un petit serveur HTTP local qui joue wallet-ledger : corps exact envoyé à
 * POST /v2/payouts, et traduction de chaque réponse possible (ACCEPTED, PENDING_APPROVAL, REJECTED,
 * 4xx, 5xx, coupure) et de la relecture GET /v2/payouts/{id}.
 */
class PawaPayClientPayoutTest {

    private static final String PAYOUT_ID = "6a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private PawaPayClient client;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        PawaPayProperties properties = new PawaPayProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiToken("wl_test_key");
        properties.setReadTimeoutMs(2000);
        client = new PawaPayClient(properties, objectMapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PayoutOutcome send(int status, String body) {
        responseStatus = status;
        responseBody = body;
        return client.initiatePayout(PAYOUT_ID, "25000", "237697272882", "ORANGE_CMR", "MLA-WD-42", "MLA RETRAIT 42");
    }

    @Test
    void sendsThePawaPayV2PayoutBody() throws Exception {
        send(200, "{\"payoutId\":\"" + PAYOUT_ID + "\",\"status\":\"ACCEPTED\"}");

        JsonNode body = objectMapper.readTree(lastBody.get());
        assertThat(body.get("payoutId").asText()).isEqualTo(PAYOUT_ID);
        assertThat(body.get("amount").asText()).isEqualTo("25000");
        assertThat(body.get("currency").asText()).isEqualTo("XAF");
        assertThat(body.at("/recipient/type").asText()).isEqualTo("MMO");
        assertThat(body.at("/recipient/accountDetails/phoneNumber").asText()).isEqualTo("237697272882");
        assertThat(body.at("/recipient/accountDetails/provider").asText()).isEqualTo("ORANGE_CMR");
        assertThat(body.get("clientReferenceId").asText()).isEqualTo("MLA-WD-42");
        assertThat(body.get("customerMessage").asText()).isEqualTo("MLA RETRAIT 42");
        assertThat(lastAuth.get()).isEqualTo("Bearer wl_test_key");
    }

    @Test
    void mapsInitiationResponses() {
        assertThat(send(200, "{\"status\":\"ACCEPTED\"}").kind()).isEqualTo(PayoutOutcome.Kind.PROCESSING);
        assertThat(send(200, "{\"status\":\"DUPLICATE_IGNORED\"}").kind()).isEqualTo(PayoutOutcome.Kind.PROCESSING);
        assertThat(send(200, "{\"status\":\"PENDING_APPROVAL\"}").kind()).isEqualTo(PayoutOutcome.Kind.PENDING_APPROVAL);

        PayoutOutcome rejected = send(200,
            "{\"status\":\"REJECTED\",\"failureReason\":{\"failureCode\":\"RECIPIENT_NOT_ALLOWED\",\"failureMessage\":\"non\"}}");
        assertThat(rejected.kind()).isEqualTo(PayoutOutcome.Kind.FAILED);
        assertThat(rejected.failureCode()).isEqualTo("RECIPIENT_NOT_ALLOWED");

        PayoutOutcome invalid = send(400,
            "{\"status\":\"REJECTED\",\"failureReason\":{\"failureCode\":\"INVALID_AMOUNT\",\"failureMessage\":\"x\"}}");
        assertThat(invalid.kind()).isEqualTo(PayoutOutcome.Kind.FAILED);
        assertThat(invalid.failureCode()).isEqualTo("INVALID_AMOUNT");

        PayoutOutcome unauthorized = send(401, "");
        assertThat(unauthorized.kind()).isEqualTo(PayoutOutcome.Kind.FAILED);
        assertThat(unauthorized.failureCode()).isEqualTo("HTTP_401");

        // wallet-ledger : PawaPay injoignable => 503 + PROVIDER_TEMPORARILY_UNAVAILABLE ; statut inconnu.
        assertThat(send(503, "{\"payoutId\":\"" + PAYOUT_ID + "\",\"failureReason\":{\"failureCode\":\"PROVIDER_TEMPORARILY_UNAVAILABLE\"}}")
            .kind()).isEqualTo(PayoutOutcome.Kind.UNAVAILABLE);
        assertThat(send(200, "").kind()).isEqualTo(PayoutOutcome.Kind.UNAVAILABLE);
    }

    @Test
    void networkFailureIsUnavailableNotFailed() {
        server.stop(0);
        assertThat(client.initiatePayout(PAYOUT_ID, "25000", "237697272882", "ORANGE_CMR", "MLA-WD-42", "MLA RETRAIT 42").kind())
            .isEqualTo(PayoutOutcome.Kind.UNAVAILABLE);
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.UNAVAILABLE);
        assertThat(client.balance()).isEmpty();
    }

    @Test
    void mapsStatusReads() {
        responseStatus = 200;
        responseBody = "{\"status\":\"FOUND\",\"data\":{\"payoutId\":\"" + PAYOUT_ID + "\",\"status\":\"COMPLETED\"}}";
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.COMPLETED);

        responseBody = "{\"status\":\"FOUND\",\"data\":{\"status\":\"FAILED\",\"failureReason\":{\"failureCode\":\"PAYOUT_REJECTED\"}}}";
        PayoutOutcome failed = client.checkPayout(PAYOUT_ID);
        assertThat(failed.kind()).isEqualTo(PayoutOutcome.Kind.FAILED);
        assertThat(failed.failureCode()).isEqualTo("PAYOUT_REJECTED");

        responseBody = "{\"status\":\"FOUND\",\"data\":{\"status\":\"PENDING_APPROVAL\"}}";
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.PENDING_APPROVAL);

        responseBody = "{\"status\":\"FOUND\",\"data\":{\"status\":\"ENQUEUED\"}}";
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.PROCESSING);

        responseBody = "{\"status\":\"NOT_FOUND\"}";
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.NOT_FOUND);

        responseStatus = 502;
        assertThat(client.checkPayout(PAYOUT_ID).kind()).isEqualTo(PayoutOutcome.Kind.UNAVAILABLE);
    }

    @Test
    void readsTheProjectBalance() {
        responseBody = "{\"project\":\"MLA\",\"currency\":\"XAF\",\"available\":\"120000\",\"reserved\":\"25000\","
            + "\"pendingDeposits\":\"0\",\"asOf\":\"2026-11-20T10:00:00Z\"}";
        MobileMoneyBalance balance = client.balance().orElseThrow();
        assertThat(balance.available()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(balance.reserved()).isEqualByComparingTo(new BigDecimal("25000"));
        assertThat(balance.currency()).isEqualTo("XAF");
    }
}
