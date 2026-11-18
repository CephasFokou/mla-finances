package org.mla.finance.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.payment.pawapay.DepositOutcome;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.service.MonthlyContributionService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contribution mensuelle payée sur le site public (caisse MLA) : offre publique, création d'une
 * contribution par mois à la confirmation (PawaPay ou secrétariat), idempotence, et contrôles.
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
class MonthlyContributionFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DonorRepository donorRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired ContributionRepository contributionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockBean PawaPayClient client;

    Donor responsable;
    Donor membre;
    Donor inactif;
    Donor tiers;
    long orangeMoney;
    long cash;
    YearMonth current;

    private static RequestPostProcessor as(String... permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", List.of(permissions)))
            .authorities(List.of(permissions).stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    private Donor person(String name, MemberRole role, MemberStatus status) {
        return donorRepository.save(Donor.builder().displayName(name).fullName(name + " Nom Complet").phone("2376" + Math.abs(name.hashCode() % 100000000))
            .memberRole(role).memberStatus(status).build());
    }

    @BeforeEach
    void setUp() {
        responsable = person("Test Resp Mensuel", MemberRole.RESPONSABLE, MemberStatus.ACTIVE);
        membre = person("Test Membre Mensuel", MemberRole.MEMBRE, MemberStatus.ACTIVE);
        inactif = person("Test Parti Mensuel", MemberRole.MEMBRE, MemberStatus.LEFT);
        tiers = person("Test Tiers Mensuel", null, null);
        orangeMoney = paymentChannelRepository.findByCode("ORANGE_MONEY").orElseThrow().getId();
        cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
        current = YearMonth.now(MonthlyContributionService.ZONE);
        when(client.initiateDeposit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(DepositOutcome.pending("ACCEPTED"));
    }

    private Map<String, Object> monthly(Long donorId, long channelId, Object amountPerMonth, List<String> months, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "MONTHLY_CONTRIBUTION");
        body.put("paymentChannelId", channelId);
        if (donorId != null) body.put("donorId", donorId);
        if (months != null) body.put("months", months);
        if (amountPerMonth != null) body.put("amountPerMonth", amountPerMonth);
        if (phone != null) body.put("payerPhone", phone);
        return body;
    }

    private JsonNode submit(Map<String, Object> body, int expectedStatus) throws Exception {
        String content = mockMvc.perform(post("/api/v1/public/donations").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private PaymentTransaction tx(JsonNode response) {
        return paymentTransactionRepository.findByTrackingToken(response.get("trackingToken").asText()).orElseThrow();
    }

    private List<Contribution> contributionsOf(PaymentTransaction tx) {
        return contributionRepository.findAll().stream()
            .filter(c -> c.getPaymentTransaction() != null && c.getPaymentTransaction().getId().equals(tx.getId()))
            .sorted((a, b) -> a.getPeriodMonth().compareTo(b.getPeriodMonth()))
            .toList();
    }

    @Test
    void offerListsActiveMembersByUsualNameWithAmountSuggestedByRole() throws Exception {
        String content = mockMvc.perform(get("/api/v1/public/monthly-contribution")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode offer = objectMapper.readTree(content);
        assertThat(offer.get("enabled").asBoolean()).isTrue();

        Map<String, JsonNode> byName = new HashMap<>();
        offer.get("members").forEach(m -> byName.put(m.get("displayName").asText(), m));
        assertThat(byName.get("Test Resp Mensuel").get("suggestedAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(byName.get("Test Membre Mensuel").get("suggestedAmount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(byName).doesNotContainKeys("Test Parti Mensuel", "Test Tiers Mensuel");
        // Nom usuel uniquement : ni rôle, ni téléphone, ni nom complet.
        assertThat(content).doesNotContain("memberRole", "phone", "fullName", "Nom Complet");

        JsonNode months = offer.get("months");
        assertThat(months.get(0).asText()).isEqualTo(current.getYear() + "-01");
        assertThat(months.get(months.size() - 1).asText()).isEqualTo(current.plusMonths(3).toString());
    }

    @Test
    void onlinePaymentForTwoMonthsCreatesTwoContributionsOnceEvenWhenCallbackIsReplayed() throws Exception {
        List<String> months = List.of(current.toString(), current.plusMonths(1).toString());
        JsonNode response = submit(monthly(membre.getId(), orangeMoney, 2000, months, "693456781"), 200);
        assertThat(response.get("status").asText()).isEqualTo("PROCESSING");

        ArgumentCaptor<String> amount = ArgumentCaptor.forClass(String.class);
        verify(client).initiateDeposit(anyString(), amount.capture(), anyString(), anyString(), anyString(), anyString());
        assertThat(amount.getValue()).isEqualTo("4000");

        PaymentTransaction tx = tx(response);
        assertThat(contributionsOf(tx)).isEmpty();
        when(client.checkDeposit(tx.getDepositId())).thenReturn(DepositOutcome.completed("COMPLETED", "PROV-M1"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/webhooks/pawapay/cb-secret-test").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("depositId", tx.getDepositId(), "status", "COMPLETED"))))
                .andExpect(status().isOk());
        }

        assertThat(paymentTransactionRepository.findById(tx.getId()).orElseThrow().getStatus()).isEqualTo(PaymentTransactionStatus.CONFIRMED);
        List<Contribution> created = contributionsOf(tx);
        assertThat(created).hasSize(2);
        DonationCategory caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow();
        assertThat(created).allSatisfy(c -> {
            assertThat(c.getAmount()).isEqualByComparingTo("2000");
            assertThat(c.getNature()).isEqualTo(MovementNature.MONTHLY_CONTRIBUTION);
            assertThat(c.getCategory().getId()).isEqualTo(caisse.getId());
            assertThat(c.getProject()).isNull();
            assertThat(c.getDonor().getId()).isEqualTo(membre.getId());
            assertThat(c.getTransactionDate()).isEqualTo(LocalDate.now(MonthlyContributionService.ZONE));
        });
        assertThat(created.get(0).getPeriodMonth()).isEqualTo(current.atDay(1));
        assertThat(created.get(1).getPeriodMonth()).isEqualTo(current.plusMonths(1).atDay(1));
        assertThat(auditLogRepository.findAll()).anyMatch(a -> "AUTO_CONFIRM".equals(a.getAction()) && tx.getId().equals(a.getEntityId()));
    }

    @Test
    void failedPaymentCreatesNothing() throws Exception {
        JsonNode response = submit(monthly(membre.getId(), orangeMoney, 2000, List.of(current.toString()), "693456782"), 200);
        PaymentTransaction tx = tx(response);
        when(client.checkDeposit(tx.getDepositId())).thenReturn(DepositOutcome.failed("FAILED", "PAYMENT_NOT_APPROVED", "refusé"));
        mockMvc.perform(post("/api/v1/webhooks/pawapay/cb-secret-test").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("depositId", tx.getDepositId(), "status", "FAILED"))))
            .andExpect(status().isOk());
        assertThat(paymentTransactionRepository.findById(tx.getId()).orElseThrow().getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(contributionsOf(tx)).isEmpty();
    }

    @Test
    void manualChannelConfirmedBySecretariatCreatesOneContributionPerMonth() throws Exception {
        List<String> months = List.of(current.getYear() + "-01", current.getYear() + "-02", current.toString());
        List<String> distinct = months.stream().distinct().toList();
        JsonNode response = submit(monthly(responsable.getId(), cash, 5000, distinct, null), 200);
        assertThat(response.get("mode").asText()).isEqualTo("MANUAL");
        PaymentTransaction tx = tx(response);
        assertThat(tx.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000L * distinct.size()));

        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/confirm").with(as("MANAGE_CONTRIBUTIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("MONTHLY_CONTRIBUTION"))
            .andExpect(jsonPath("$.member.displayName").value("Test Resp Mensuel"))
            .andExpect(jsonPath("$.months.length()").value(distinct.size()));
        List<Contribution> created = contributionsOf(tx);
        assertThat(created).hasSize(distinct.size());
        assertThat(created).allSatisfy(c -> assertThat(c.getAmount()).isEqualByComparingTo("5000"));

        // Seconde confirmation refusée : rien de plus.
        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/confirm").with(as("MANAGE_CONTRIBUTIONS")))
            .andExpect(status().isUnprocessableEntity());
        assertThat(contributionsOf(tx)).hasSize(distinct.size());
    }

    @Test
    void backOfficeWarnsWhenAMonthIsAlreadyPaidByTheMember() throws Exception {
        DonationCategory caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow();
        contributionRepository.save(Contribution.builder().category(caisse).donor(membre)
            .paymentChannel(paymentChannelRepository.findById(cash).orElseThrow()).amount(new BigDecimal("2000"))
            .periodMonth(current.atDay(1)).transactionDate(LocalDate.now()).movementType(MovementType.STANDARD)
            .nature(MovementNature.MONTHLY_CONTRIBUTION).build());
        JsonNode response = submit(monthly(membre.getId(), cash, 2000, List.of(current.toString(), current.plusMonths(1).toString()), null), 200);
        PaymentTransaction tx = tx(response);

        mockMvc.perform(post("/api/v1/payment-transactions/" + tx.getId() + "/confirm").with(as("MANAGE_CONTRIBUTIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alreadyPaidMonths.length()").value(1))
            .andExpect(jsonPath("$.alreadyPaidMonths[0]").value(current.toString()));
        // On crée quand même : le secrétariat corrige si besoin.
        assertThat(contributionsOf(tx)).hasSize(2);
    }

    @Test
    void missingMemberMonthsOrAmountIsABadRequest() throws Exception {
        submit(monthly(null, orangeMoney, 2000, List.of(current.toString()), "693456783"), 400);
        submit(monthly(membre.getId(), orangeMoney, 2000, null, "693456783"), 400);
        submit(monthly(membre.getId(), orangeMoney, 2000, List.of(), "693456783"), 400);
        submit(monthly(membre.getId(), orangeMoney, null, List.of(current.toString()), "693456783"), 400);
        submit(monthly(membre.getId(), orangeMoney, 2000, List.of(current.toString(), current.toString()), "693456783"), 400);
        submit(monthly(membre.getId(), orangeMoney, 2000, List.of("2026/11"), "693456783"), 400);
    }

    @Test
    void nonMemberInactiveMemberOrMonthOutOfRangeIsRefused() throws Exception {
        JsonNode r1 = submit(monthly(tiers.getId(), orangeMoney, 2000, List.of(current.toString()), "693456784"), 422);
        assertThat(r1.get("rule").asText()).isEqualTo("DON-10");
        submit(monthly(inactif.getId(), orangeMoney, 2000, List.of(current.toString()), "693456784"), 422);
        submit(monthly(999_999L, orangeMoney, 2000, List.of(current.toString()), "693456784"), 422);
        JsonNode r2 = submit(monthly(membre.getId(), orangeMoney, 2000, List.of(current.plusMonths(4).toString()), "693456784"), 422);
        assertThat(r2.get("rule").asText()).isEqualTo("DON-11");
        submit(monthly(membre.getId(), orangeMoney, 2000, List.of((current.getYear() - 1) + "-12"), "693456784"), 422);
        // Total hors bornes PawaPay (DON-05).
        JsonNode r3 = submit(monthly(membre.getId(), orangeMoney, 5_000_000, List.of(current.toString()), "693456784"), 422);
        assertThat(r3.get("rule").asText()).isEqualTo("DON-05");
    }

    @Test
    void administrationCanDisableTheJourneyAndChangeSuggestedAmounts() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("enabled", false, "responsableAmount", 6000, "memberAmount", 2500));
        mockMvc.perform(put("/api/v1/settings/monthly-contribution").with(as("MANAGE_CONTRIBUTIONS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/settings/monthly-contribution").with(as("MANAGE_USERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/public/monthly-contribution"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.members.length()").value(0));
        JsonNode refused = submit(monthly(membre.getId(), orangeMoney, 2000, List.of(current.toString()), "693456785"), 422);
        assertThat(refused.get("rule").asText()).isEqualTo("DON-09");

        mockMvc.perform(put("/api/v1/settings/monthly-contribution").with(as("MANAGE_USERS")).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true, "responsableAmount", 6000, "memberAmount", 2500))))
            .andExpect(status().isOk());
        String content = mockMvc.perform(get("/api/v1/public/monthly-contribution")).andReturn().getResponse().getContentAsString();
        JsonNode offer = objectMapper.readTree(content);
        offer.get("members").forEach(m -> {
            if (m.get("displayName").asText().equals("Test Resp Mensuel")) {
                assertThat(m.get("suggestedAmount").decimalValue()).isEqualByComparingTo("6000");
            }
            if (m.get("displayName").asText().equals("Test Membre Mensuel")) {
                assertThat(m.get("suggestedAmount").decimalValue()).isEqualByComparingTo("2500");
            }
        });
    }

    @Test
    void collectionDonationStillRequiresAProject() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("paymentChannelId", cash);
        body.put("amount", 1000);
        body.put("donorName", "Sans collecte");
        submit(body, 400);
    }
}
