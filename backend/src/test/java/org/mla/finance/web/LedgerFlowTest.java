package org.mla.finance.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.ActivityStatus;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.repository.ActivityRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours du fichier de suivi MLA (règles confirmées le 05/11/2026) : caisse principale séparée des
 * collectes, contributions mensuelles par mois d'attribution, sorties déjà effectuées, transferts
 * internes (RG-10), activités de la caisse, et rapports Synthèse / Calendrier / Collectes / Activités.
 * Les rapports portant sur toute la base, chaque test raisonne en écarts (avant / après).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class LedgerFlowTest {

    private static final List<String> ALL = List.of("MANAGE_CONTRIBUTIONS", "MANAGE_PROJECTS", "MANAGE_WITHDRAWALS",
        "VALIDATE_WITHDRAWALS", "MANAGE_USERS", "VIEW_DASHBOARD");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired DonorRepository donorRepository;
    @Autowired ActivityRepository activityRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;

    DonationCategory caisse;
    Project collecte;
    Donor responsable;
    Donor membre;
    long cash;

    private static RequestPostProcessor as(List<String> permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", permissions))
            .authorities(permissions.stream().map(p -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    @BeforeEach
    void setUp() {
        caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow();
        collecte = projectRepository.save(Project.builder().name("Main levée test").status(ProjectStatus.ACTIVE).build());
        responsable = donorRepository.save(Donor.builder().displayName("Resp test").fullName("Resp Test")
            .memberRole(MemberRole.RESPONSABLE).memberStatus(MemberStatus.ACTIVE).build());
        membre = donorRepository.save(Donor.builder().displayName("Membre test").fullName("Membre Test")
            .memberRole(MemberRole.MEMBRE).memberStatus(MemberStatus.ACTIVE).build());
        cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
    }

    private JsonNode postJson(String url, Object body, List<String> permissions, int expectedStatus) throws Exception {
        String content = mockMvc.perform(post(url).with(as(permissions)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : objectMapper.readTree(content);
    }

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).with(as(ALL))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode contribution(Long categoryId, Long projectId, Long donorId, String nature, int amount, String period,
                                  String date) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("categoryId", categoryId);
        body.put("projectId", projectId);
        body.put("donorId", donorId);
        body.put("paymentChannelId", cash);
        body.put("amount", amount);
        body.put("periodMonth", period);
        body.put("transactionDate", date);
        body.put("nature", nature);
        return postJson("/api/v1/contributions", body, ALL, 201);
    }

    private double mainFundBalance() throws Exception {
        return getJson("/api/v1/categories/" + caisse.getId() + "/balance").get("currentBalance").asDouble();
    }

    @Test
    void monthlyContributionCountsForItsAttributionMonthAndOnlyInTheMainFund() throws Exception {
        JsonNode before = getJson("/api/v1/reports/summary?year=2025");

        // Contribution de mars 2025 déposée en avril : comptée en mars.
        contribution(caisse.getId(), null, responsable.getId(), "MONTHLY_CONTRIBUTION", 5000, "2025-03", "2025-04-02");
        contribution(caisse.getId(), null, membre.getId(), "MONTHLY_CONTRIBUTION", 2000, "2025-03", "2025-03-20");

        JsonNode after = getJson("/api/v1/reports/summary?year=2025");
        JsonNode march = after.get("months").get(2);
        JsonNode marchBefore = before.get("months").get(2);
        assertThat(march.get("month").asText()).isEqualTo("2025-03");
        assertThat(march.get("responsablesAmount").asDouble() - marchBefore.get("responsablesAmount").asDouble()).isEqualTo(5000);
        assertThat(march.get("membersAmount").asDouble() - marchBefore.get("membersAmount").asDouble()).isEqualTo(2000);
        assertThat(after.get("months").get(3).get("total").asDouble())
            .isEqualTo(before.get("months").get(3).get("total").asDouble());

        // Calendrier : la ligne du responsable porte 5 000 en mars.
        JsonNode calendar = getJson("/api/v1/reports/monthly-calendar?year=2025");
        JsonNode row = null;
        for (JsonNode r : calendar.get("rows")) {
            if (r.get("donorId").asLong() == responsable.getId()) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("amounts").get(2).asDouble()).isEqualTo(5000);
        assertThat(row.get("monthsCount").asInt()).isEqualTo(1);

        // Une contribution mensuelle sur une collecte est refusée.
        Map<String, Object> wrong = Map.of("projectId", collecte.getId(), "paymentChannelId", cash, "amount", 1000,
            "periodMonth", "2025-03", "transactionDate", "2025-03-20", "nature", "MONTHLY_CONTRIBUTION");
        postJson("/api/v1/contributions", wrong, ALL, 422);
    }

    @Test
    void cancellationKeepsTheAttributionMonthSoTheCalendarNetsOut() throws Exception {
        JsonNode created = contribution(caisse.getId(), null, membre.getId(), "MONTHLY_CONTRIBUTION", 3000, "2024-06", "2024-06-10");
        JsonNode adjustment = postJson("/api/v1/contributions/" + created.get("id").asLong() + "/cancel",
            Map.of("reason", "Doublon"), ALL, 201);
        assertThat(adjustment.get("periodMonth").asText()).isEqualTo("2024-06-01");
        assertThat(adjustment.get("nature").asText()).isEqualTo("MONTHLY_CONTRIBUTION");
        assertThat(adjustment.get("donor").get("id").asLong()).isEqualTo(membre.getId());

        JsonNode june = getJson("/api/v1/reports/summary?year=2024").get("months").get(5);
        assertThat(june.get("membersContributors").asInt()).isZero();
    }

    @Test
    void recordedExpenseIsProcessedAtItsRealDateAndFeedsTheActivityReport() throws Exception {
        Activity comfrat = activityRepository.save(Activity.builder().name("ComFrat test").status(ActivityStatus.COMPLETED).build());
        double before = mainFundBalance();

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("categoryId", caisse.getId());
        body.put("amount", 59800);
        body.put("reason", "Organisation de la ComFrat");
        body.put("nature", "EXPENSE");
        body.put("personId", responsable.getId());
        body.put("paymentChannelId", cash);
        body.put("activityId", comfrat.getId());
        body.put("operationDate", "2026-09-25");
        JsonNode recorded = postJson("/api/v1/withdrawals/record", body, ALL, 201);
        assertThat(recorded.get("status").asText()).isEqualTo("PROCESSED");
        assertThat(recorded.get("operationDate").asText()).isEqualTo("2026-09-25");
        assertThat(recorded.get("person").get("displayName").asText()).isEqualTo("Resp test");

        // Retour d'argent rattaché à la même activité.
        Map<String, Object> back = new java.util.HashMap<>();
        back.put("categoryId", caisse.getId());
        back.put("donorId", responsable.getId());
        back.put("paymentChannelId", cash);
        back.put("amount", 1000);
        back.put("periodMonth", "2026-09");
        back.put("transactionDate", "2026-09-27");
        back.put("nature", "RETURN");
        back.put("activityId", comfrat.getId());
        postJson("/api/v1/contributions", back, ALL, 201);

        assertThat(mainFundBalance()).isEqualTo(before - 58800);
        JsonNode activity = null;
        for (JsonNode r : getJson("/api/v1/reports/activities")) {
            if (r.get("activity").get("id").asLong() == comfrat.getId()) {
                activity = r;
            }
        }
        assertThat(activity).isNotNull();
        assertThat(activity.get("expenses").asDouble()).isEqualTo(59800);
        assertThat(activity.get("returns").asDouble()).isEqualTo(1000);
        assertThat(activity.get("netCost").asDouble()).isEqualTo(58800);

        // Saisie a posteriori : réservée à qui peut aussi valider les retraits.
        postJson("/api/v1/withdrawals/record", body, List.of("MANAGE_WITHDRAWALS"), 403);
        // Une entrée d'argent n'est pas une sortie.
        body.put("nature", "DONATION");
        postJson("/api/v1/withdrawals/record", body, ALL, 422);
    }

    @Test
    void transferMovesMoneyBetweenEnvelopesWithoutChangingTheTotal() throws Exception {
        contribution(null, collecte.getId(), membre.getId(), "CONTRIBUTION", 10000, "2026-06", "2026-06-18");
        double caisseBefore = mainFundBalance();

        Map<String, Object> body = Map.of("fromProjectId", collecte.getId(), "toCategoryId", caisse.getId(),
            "amount", 4000, "operationDate", "2026-06-30", "reason", "Reliquat reversé dans la caisse");
        JsonNode transfer = postJson("/api/v1/transfers", body, ALL, 201);
        String group = transfer.get("transferGroup").asText();
        assertThat(group).startsWith("TRF-");
        assertThat(transfer.get("outflow").get("nature").asText()).isEqualTo("TRANSFER");
        assertThat(transfer.get("inflow").get("transferGroup").asText()).isEqualTo(group);

        assertThat(mainFundBalance()).isEqualTo(caisseBefore + 4000);
        JsonNode row = null;
        for (JsonNode r : getJson("/api/v1/reports/collections")) {
            if (r.get("project").get("id").asLong() == collecte.getId()) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("collected").asDouble()).isEqualTo(10000);
        assertThat(row.get("used").asDouble()).isEqualTo(4000);
        assertThat(row.get("remaining").asDouble()).isEqualTo(6000);
        assertThat(row.get("contributionsCount").asInt()).isEqualTo(1);

        // Une écriture de transfert ne s'annule pas seule.
        postJson("/api/v1/contributions/" + transfer.get("inflow").get("id").asLong() + "/cancel",
            Map.of("reason", "x"), ALL, 422);
        // Transfert vers soi-même refusé.
        postJson("/api/v1/transfers", Map.of("fromProjectId", collecte.getId(), "toProjectId", collecte.getId(),
            "amount", 1, "operationDate", "2026-06-30", "reason", "x"), ALL, 422);
    }

    @Test
    void membersAndPaymentChannelsAreManageable() throws Exception {
        JsonNode created = postJson("/api/v1/members", Map.of("displayName", "Nouvelle", "firstName", "Ana", "lastName", "NDONGO",
            "memberRole", "MEMBRE"), ALL, 201);
        assertThat(created.get("fullName").asText()).isEqualTo("Ana NDONGO");
        assertThat(created.get("memberStatus").asText()).isEqualTo("ACTIVE");
        postJson("/api/v1/members", Map.of("displayName", "nouvelle"), ALL, 409);

        mockMvc.perform(get("/api/v1/donors").with(as(ALL)).param("q", "nouv"))
            .andExpect(jsonPath("$[0].displayName").value("Nouvelle"));

        JsonNode channel = postJson("/api/v1/payment-channels", Map.of("code", "WAVE", "label", "Wave"), ALL, 201);
        postJson("/api/v1/payment-channels/" + channel.get("id").asLong() + "/deactivate", Map.of(), ALL, 200);
        mockMvc.perform(get("/api/v1/payment-channels").with(as(ALL)))
            .andExpect(jsonPath("$[?(@.code=='WAVE')]").doesNotExist())
            .andExpect(jsonPath("$[?(@.code=='OTHER')]").exists());
        long other = paymentChannelRepository.findByCode("OTHER").orElseThrow().getId();
        postJson("/api/v1/payment-channels/" + other + "/deactivate", Map.of(), ALL, 422);
        postJson("/api/v1/payment-channels", Map.of("code", "X", "label", "X"), List.of("MANAGE_CONTRIBUTIONS"), 403);
    }
}
