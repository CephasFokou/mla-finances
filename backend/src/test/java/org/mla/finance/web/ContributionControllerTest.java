package org.mla.finance.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.domain.Project;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RG-18 : depuis que l'authentification réelle est active en profil dev aussi (voir
 * security.SecurityConfig, security.DevAdminSeeder), chaque appel MockMvc doit porter un JWT —
 * {@link #asAdmin()} en injecte un avec l'autorité MANAGE_CONTRIBUTIONS directement (via
 * spring-security-test), sans passer par le vrai flux de login : ce test vérifie le comportement
 * des endpoints Contributions, pas l'authentification elle-même (couverte séparément par
 * AuthControllerTest). Exception : corsAllowsFrontendDevOrigin, qui vérifie volontairement des
 * requêtes SANS JWT — le filtre CORS de Spring rejette une requête preflight ou d'origine interdite
 * avant même d'atteindre la vérification d'authentification, donc ce test reste valide tel quel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ContributionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired DonorRepository donorRepository;

    private static RequestPostProcessor asAdmin() {
        return jwt()
            .jwt(builder -> builder.subject("999").claim("permissions", List.of("MANAGE_CONTRIBUTIONS")))
            .authorities(new SimpleGrantedAuthority("MANAGE_CONTRIBUTIONS"));
    }

    private String body(Long projectId, Long categoryId, String amount) throws Exception {
        long channelId = paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().get(0).getId();
        return objectMapper.writeValueAsString(java.util.Map.of(
            projectId != null ? "projectId" : "categoryId", projectId != null ? projectId : categoryId,
            "paymentChannelId", channelId,
            "amount", new BigDecimal(amount),
            "periodMonth", "2026-09",
            "transactionDate", "2026-09-18",
            "anonymous", false,
            "notes", "Don du culte du dimanche"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void fullCycleCreateReadCancel() throws Exception {
        Project project = projectRepository.save(Project.builder().name("Toiture").openingBalance(new BigDecimal("1000.00")).build());

        MvcResult created = mockMvc.perform(post("/api/v1/contributions").with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(body(project.getId(), null, "25000.00")))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.periodMonth").value("2026-09-01"))
            .andExpect(jsonPath("$.project.name").value("Toiture"))
            .andExpect(jsonPath("$.category").doesNotExist())
            .andExpect(jsonPath("$.paymentChannel.code").exists())
            .andExpect(jsonPath("$.movementType").value("STANDARD"))
            .andReturn();
        long id = json(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/contributions/{id}", id).with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.project.id").value(project.getId()));

        mockMvc.perform(get("/api/v1/contributions").with(asAdmin()).param("projectId", project.getId().toString()).param("periodMonth", "2026-09"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/projects/{id}/balance", project.getId()).with(asAdmin()))
            .andExpect(jsonPath("$.currentBalance").value(26000.00));

        mockMvc.perform(post("/api/v1/contributions/{id}/cancel", id).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Erreur de saisie\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.movementType").value("ADJUSTMENT"))
            .andExpect(jsonPath("$.amount").value(-25000.00))
            .andExpect(jsonPath("$.adjustsContributionId").value(id));

        mockMvc.perform(post("/api/v1/contributions/{id}/cancel", id).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Encore\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/projects/{id}/balance", project.getId()).with(asAdmin()))
            .andExpect(jsonPath("$.currentBalance").value(1000.00))
            .andExpect(jsonPath("$.contributionCount").value(2));
    }

    @Test
    void violationsMapToExpectedStatuses() throws Exception {
        Project project = projectRepository.save(Project.builder().name("P").build());
        DonationCategory category = categoryRepository.save(DonationCategory.builder().name("Cat (test ctrl)").build());
        long channelId = paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().get(0).getId();

        String both = String.format("{\"projectId\":%d,\"categoryId\":%d,\"paymentChannelId\":%d,\"amount\":10,"
            + "\"periodMonth\":\"2026-09\",\"transactionDate\":\"2026-09-18\"}", project.getId(), category.getId(), channelId);
        mockMvc.perform(post("/api/v1/contributions").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(both))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.rule").value("RG-02"));

        mockMvc.perform(post("/api/v1/contributions").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body(project.getId(), null, "-5")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("amount"))
            .andExpect(jsonPath("$.errors[0].message").exists());

        mockMvc.perform(get("/api/v1/contributions/{id}", 999999).with(asAdmin())).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/contributions").with(asAdmin()).param("sort", "nope,asc")).andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/v1/contributions").with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(body(null, category.getId(), "100.00")))
            .andExpect(status().isCreated()).andReturn();
        long id = json(created).get("id").asLong();
        mockMvc.perform(put("/api/v1/contributions/{id}", id).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void referenceDataEndpointsReturnPlainArrays() throws Exception {
        projectRepository.save(Project.builder().name("Toiture ref").build());
        categoryRepository.save(DonationCategory.builder().name("Cat active ref").build());
        categoryRepository.save(DonationCategory.builder().name("Cat inactive ref").active(false).build());

        mockMvc.perform(get("/api/v1/projects").with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='Toiture ref')].status").value("DRAFT"));
        mockMvc.perform(get("/api/v1/categories").with(asAdmin()))
            .andExpect(jsonPath("$[?(@.name=='Cat active ref')]").exists())
            .andExpect(jsonPath("$[?(@.name=='Cat inactive ref')]").doesNotExist());
        mockMvc.perform(get("/api/v1/payment-channels").with(asAdmin()))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].code").exists())
            .andExpect(jsonPath("$[0].label").exists());
        mockMvc.perform(get("/api/v1/donors").with(asAdmin())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/v1/donors").with(asAdmin()).param("q", "zzz-aucun-donateur")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void donorSearchIsCaseInsensitiveAndNestedInContribution() throws Exception {
        Donor donor = donorRepository.save(Donor.builder().fullName("Jean-Paul Mbarga").build());
        DonationCategory category = categoryRepository.save(DonationCategory.builder().name("Cat donor").build());
        long channelId = paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().get(0).getId();

        mockMvc.perform(get("/api/v1/donors").with(asAdmin()).param("q", "paul MBA"))
            .andExpect(jsonPath("$[0].id").value(donor.getId()))
            .andExpect(jsonPath("$[0].fullName").value("Jean-Paul Mbarga"));

        mockMvc.perform(post("/api/v1/contributions").with(asAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"categoryId\":%d,\"donorId\":%d,\"paymentChannelId\":%d,\"amount\":10,"
                    + "\"periodMonth\":\"2026-09\",\"transactionDate\":\"2026-09-18\"}", category.getId(), donor.getId(), channelId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.donor.fullName").value("Jean-Paul Mbarga"))
            .andExpect(jsonPath("$.project").doesNotExist())
            .andExpect(jsonPath("$.category.name").value("Cat donor"));
    }

    @Test
    void corsAllowsFrontendDevOrigin() throws Exception {
        // Volontairement sans JWT : une requête preflight (OPTIONS) ou d'origine interdite est
        // rejetée par le CorsFilter de Spring, en amont du filtre d'autorisation — voir la javadoc
        // de la classe.
        mockMvc.perform(options("/api/v1/contributions")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(get("/api/v1/projects").header("Origin", "http://evil.example"))
            .andExpect(status().isForbidden());
    }

    @Test
    void exportProducesCsvWithHeaderAndRow() throws Exception {
        DonationCategory category = categoryRepository.save(DonationCategory.builder().name("Cat export").build());
        mockMvc.perform(post("/api/v1/contributions").with(asAdmin())
            .contentType(MediaType.APPLICATION_JSON).content(body(null, category.getId(), "100.00"))).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/contributions/export").with(asAdmin()).param("categoryId", category.getId().toString()))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        String[] lines = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines[0]).isEqualTo("Date;Mois d'attribution;Projet;Catégorie;Donateur;Montant;Canal;Nature;Type;Activité;Anonyme;Notes;Remarque");
        assertThat(lines[1]).startsWith("2026-09-18;2026-09;;Cat export;;100.00;");
    }
}
