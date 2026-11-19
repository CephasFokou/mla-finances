package org.mla.finance.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.payment.pawapay.DepositOutcome;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours d'un don payé en ligne via PawaPay, avec le client HTTP simulé : initiation, résolution
 * par callback puis relecture (idempotence RG-20), échec, validations anti-abus, et frontière
 * entre mode manuel et mode en ligne. Aucun appel réseau réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.payment.pawapay.enabled=true",
    "app.payment.pawapay.api-token=test-token",
    "app.payment.pawapay.callback-secret=cb-secret-test",
    "app.payment.pawapay.max-initiations-per-phone=3",
    "app.payment.pawapay.poll-interval-ms=3600000",
    "app.payment.pawapay.poll-initial-delay-ms=3600000",
    "app.rate-limit.public-requests-per-minute=10000",
    "app.rate-limit.webhook-requests-per-minute=10000"
})
@Transactional
class PawaPayFlowTest {

    private static final List<String> MANAGE = List.of("MANAGE_CONTRIBUTIONS");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired ContributionRepository contributionRepository;
    @MockBean PawaPayClient client;

    Project collecte;
    long orangeMoney;
    long cash;

    private static RequestPostProcessor as(List<String> permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", permissions))
            .authorities(permissions.stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    @BeforeEach
    void setUp() {
        collecte = projectRepository.save(Project.builder().name("Collecte test PawaPay").status(ProjectStatus.ACTIVE).build());
        orangeMoney = paymentChannelRepository.findByCode("ORANGE_MONEY").orElseThrow().getId();
        cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
        when(client.initiateDeposit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(DepositOutcome.pending("ACCEPTED"));
    }

    private Map<String, Object> donation(long channelId, Object amount, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", collecte.getId());
        body.put("paymentChannelId", channelId);
        body.put("amount", amount);
        body.put("anonymous", false);
        body.put("donorName", "Testeur PawaPay");
        body.put("payerPhone", phone);
        return body;
    }

    private JsonNode submit(Map<String, Object> body, int expectedStatus) throws Exception {
        String content = mockMvc.perform(post("/api/v1/public/donations").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private void callback(String secret, String depositId, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/pawapay/" + secret).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("depositId", depositId, "status", "COMPLETED"))))
            .andExpect(status().is(expectedStatus));
    }

    private BigDecimal collected() {
        return contributionRepository.sumAmountByProjectId(collecte.getId());
    }

    @Test
    void publicChannelsExposeTheirPaymentMode() throws Exception {
        String content = mockMvc.perform(get("/api/v1/public/payment-channels")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode channels = objectMapper.readTree(content);
        boolean orangeOnline = false;
        boolean cashOnline = true;
        boolean otherPresent = false;
        for (JsonNode c : channels) {
            if (c.get("code").asText().equals("ORANGE_MONEY")) orangeOnline = c.get("online").asBoolean();
            if (c.get("code").asText().equals("CASH")) cashOnline = c.get("online").asBoolean();
            if (c.get("code").asText().equals("OTHER")) otherPresent = true;
        }
        assertThat(orangeOnline).isTrue();
        assertThat(cashOnline).isFalse();
        assertThat(otherPresent).isFalse();
    }

    @Test
    void onlineDonationIsInitiatedWithNormalizedPhoneAndCreatesNoContributionYet() throws Exception {
        BigDecimal before = collected();

        JsonNode response = submit(donation(orangeMoney, 2000, "6 93 45 67 89"), 200);
        assertThat(response.get("mode").asText()).isEqualTo("ONLINE");
        assertThat(response.get("status").asText()).isEqualTo("PROCESSING");
        assertThat(response.get("trackingToken").asText()).hasSize(36);

        ArgumentCaptor<String> phone = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> provider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> amount = ArgumentCaptor.forClass(String.class);
        verify(client).initiateDeposit(anyString(), amount.capture(), phone.capture(), provider.capture(), anyString(), anyString());
        assertThat(phone.getValue()).isEqualTo("237693456789");
        assertThat(provider.getValue()).isEqualTo("ORANGE_CMR");
        assertThat(amount.getValue()).isEqualTo("2000");

        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        assertThat(tx.getDepositId()).hasSize(36);
        assertThat(tx.getProvider()).isEqualTo("PAWAPAY");
        assertThat(tx.getNextCheckAt()).isNotNull();
        assertThat(collected()).isEqualByComparingTo(before);

        // Suivi public : statut seulement, jamais de donnée interne.
        mockMvc.perform(get("/api/v1/public/donations/" + tx.getTrackingToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.reference").value(tx.getReference()));
    }

    private PaymentTransaction confirmOnline(Map<String, Object> body) throws Exception {
        JsonNode response = submit(body, 200);
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        when(client.checkDeposit(tx.getDepositId())).thenReturn(DepositOutcome.completed("COMPLETED", "PROV-" + tx.getId()));
        callback("cb-secret-test", tx.getDepositId(), 200);
        return tx;
    }

    @Test
    void confirmedDonationExposesItsReceiptAndTheSiteNeverNamesDonors() throws Exception {
        Map<String, Object> named = donation(orangeMoney, 3000, "693456789");
        named.put("donorName", "samuel PIEDJOU");
        PaymentTransaction tx = confirmOnline(named);

        // Reçu : lu par le jeton du payeur, numéro masqué.
        mockMvc.perform(get("/api/v1/public/donations/" + tx.getTrackingToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.kind").value("DONATION"))
            .andExpect(jsonPath("$.purpose").value("Collecte test PawaPay"))
            .andExpect(jsonPath("$.donorName").value("samuel PIEDJOU"))
            .andExpect(jsonPath("$.payerPhone").value("6** ** 67 89"))
            .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
        JsonNode receipt = objectMapper.readTree(mockMvc.perform(get("/api/v1/public/donations/" + tx.getTrackingToken()))
            .andReturn().getResponse().getContentAsString());
        assertThat(receipt.get("amount").decimalValue()).isEqualByComparingTo("3000");

        Map<String, Object> anonymous = donation(orangeMoney, 1000, "693456788");
        anonymous.put("anonymous", true);
        anonymous.remove("donorName");
        PaymentTransaction anon = confirmOnline(anonymous);
        mockMvc.perform(get("/api/v1/public/donations/" + anon.getTrackingToken()))
            .andExpect(jsonPath("$.donorName").value(org.hamcrest.Matchers.nullValue()));

        // Site public : totaux seulement, aucun nom de donateur (anonymat pour tous).
        String content = mockMvc.perform(get("/api/v1/public/collections")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(content).doesNotContain("PIEDJOU").doesNotContain("Samuel").doesNotContain("recentDonors");
    }

    @Test
    void completedDepositConfirmsOnceEvenWhenCallbackIsReplayed() throws Exception {
        BigDecimal before = collected();
        JsonNode response = submit(donation(orangeMoney, 5000, "237693456789"), 200);
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        when(client.checkDeposit(tx.getDepositId())).thenReturn(DepositOutcome.completed("COMPLETED", "PROV-1"));

        // Mauvais secret : indistinguable d'une route inexistante, et PawaPay n'est pas interrogé.
        callback("wrong-secret", tx.getDepositId(), 404);
        verify(client, never()).checkDeposit(anyString());

        callback("cb-secret-test", tx.getDepositId(), 200);
        callback("cb-secret-test", tx.getDepositId(), 200); // rejeu
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/recheck").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.contributionId").isNumber());

        PaymentTransaction after = paymentTransactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentTransactionStatus.CONFIRMED);
        assertThat(after.getContribution()).isNotNull();
        assertThat(after.getProviderReference()).isEqualTo("PROV-1");
        assertThat(collected()).isEqualByComparingTo(before.add(new BigDecimal("5000")));
        // Une seule relecture a réellement eu lieu : les suivantes ont vu un statut final.
        verify(client, times(1)).checkDeposit(tx.getDepositId());

        // Un don en ligne ne se confirme ni ne s'annule à la main.
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/confirm").with(as(MANAGE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.rule").value("DON-07"));
    }

    @Test
    void failedDepositIsFinalAndExposesAFriendlyMessage() throws Exception {
        BigDecimal before = collected();
        JsonNode response = submit(donation(orangeMoney, 1000, "237653456039"), 200);
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        when(client.checkDeposit(tx.getDepositId()))
            .thenReturn(DepositOutcome.failed("FAILED", "PAYMENT_NOT_APPROVED", "The customer did not approve"));

        callback("cb-secret-test", tx.getDepositId(), 200);

        mockMvc.perform(get("/api/v1/public/donations/" + tx.getTrackingToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureCode").value("PAYMENT_NOT_APPROVED"))
            .andExpect(jsonPath("$.failureMessage").value(org.hamcrest.Matchers.containsString("validé")));
        assertThat(collected()).isEqualByComparingTo(before);
        assertThat(paymentTransactionRepository.findById(tx.getId()).orElseThrow().getNextCheckAt()).isNull();

        // Un don FAILED ne peut pas être annulé à la main (statut final).
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/cancel").with(as(MANAGE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectedInitiationFailsImmediatelyWithoutPolling() throws Exception {
        when(client.initiateDeposit(anyString(), anyString(), eq("237653456029"), anyString(), anyString(), anyString()))
            .thenReturn(DepositOutcome.failed("REJECTED", "PAYER_NOT_FOUND", "no wallet"));

        JsonNode response = submit(donation(orangeMoney, 1000, "653456029"), 200);
        assertThat(response.get("status").asText()).isEqualTo("FAILED");
        assertThat(response.get("failureMessage").asText()).contains("compte Mobile Money");
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        assertThat(tx.getNextCheckAt()).isNull();
        assertThat(tx.getFailureCode()).isEqualTo("PAYER_NOT_FOUND");
    }

    @Test
    void unavailableProviderKeepsTheDonationProcessingForReconciliation() throws Exception {
        when(client.initiateDeposit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(DepositOutcome.unavailable("timeout"));

        JsonNode response = submit(donation(orangeMoney, 1000, "693456789"), 200);
        assertThat(response.get("status").asText()).isEqualTo("PROCESSING");
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        assertThat(tx.getNextCheckAt()).isNotNull();

        // PawaPay n'a jamais reçu la demande : la relecture conclut à l'échec.
        when(client.checkDeposit(tx.getDepositId())).thenReturn(DepositOutcome.notFound());
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/recheck").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureCode").value("DEPOSIT_NOT_FOUND"));
    }

    @Test
    void onlineDonationValidatesPhoneAmountAndThrottlesPerPhone() throws Exception {
        assertThat(submit(donation(orangeMoney, 1000, "12345"), 422).get("rule").asText()).isEqualTo("DON-04");
        assertThat(submit(donation(orangeMoney, 1000, null), 422).get("rule").asText()).isEqualTo("DON-04");
        assertThat(submit(donation(orangeMoney, "1000.50", "693456789"), 422).get("rule").asText()).isEqualTo("DON-05");
        assertThat(submit(donation(orangeMoney, 50, "693456789"), 422).get("rule").asText()).isEqualTo("DON-05");
        verify(client, never()).initiateDeposit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        submit(donation(orangeMoney, 1000, "693456789"), 200);
        submit(donation(orangeMoney, 1000, "+237 693456789"), 200);
        submit(donation(orangeMoney, 1000, "0693456789"), 200);
        assertThat(submit(donation(orangeMoney, 1000, "693456789"), 422).get("rule").asText()).isEqualTo("DON-06");
        verify(client, times(3)).initiateDeposit(anyString(), anyString(), eq("237693456789"), anyString(), anyString(), anyString());
    }

    @Test
    void manualChannelStaysManualAndNeedsNoPhone() throws Exception {
        JsonNode response = submit(donation(cash, 1500, null), 200);
        assertThat(response.get("mode").asText()).isEqualTo("MANUAL");
        assertThat(response.get("status").asText()).isEqualTo("PENDING");
        verify(client, never()).initiateDeposit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
        assertThat(tx.getDepositId()).isNull();
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/recheck").with(as(MANAGE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.rule").value("DON-07"));
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/confirm").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
