package org.mla.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.AuditLog;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sortie sur une collecte clôturée qui a encore un solde : proposée au formulaire, plafonnée au solde
 * prévisionnel (422 au-delà) à la création, à la validation et au décaissement ; la collecte reste
 * clôturée. Une collecte ouverte n'est pas concernée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WithdrawalClosedProjectTest {

    private static final List<String> BOTH = List.of("MANAGE_WITHDRAWALS", "VALIDATE_WITHDRAWALS");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired BalanceService balanceService;

    Project closed;
    User treasurer;
    User otherTreasurer;

    @BeforeEach
    void setUp() {
        closed = project("Main levée oct. 2026", ProjectStatus.COMPLETED, "17000");
        treasurer = user("tresorier-clos-a@test.local", "Awa Trésorière", "TRESORIER");
        otherTreasurer = user("tresorier-clos-b@test.local", "Bruno Trésorier", "TRESORIER");
    }

    private Project project(String name, ProjectStatus status, String opening) {
        return projectRepository.save(Project.builder().name(name).status(status)
            .openingBalance(new BigDecimal(opening)).build());
    }

    private User user(String email, String name, String role) {
        Role r = roleRepository.findByName(role).orElseThrow();
        return userRepository.save(User.builder().email(email).fullName(name).status(UserStatus.ACTIVE)
            .roles(new HashSet<>(Set.of(r))).build());
    }

    private static RequestPostProcessor as(User user, List<String> permissions) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(user.getId())).claim("permissions", permissions))
            .authorities(permissions.stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    private String body(Project project, String amount, boolean withDate) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("projectId", project.getId());
        b.put("amount", new BigDecimal(amount));
        b.put("reason", "Remise du reste à AE Merlin");
        if (withDate) {
            b.put("operationDate", LocalDate.now().toString());
        }
        return objectMapper.writeValueAsString(b);
    }

    private long createRequest(Project project, String amount) throws Exception {
        String json = mockMvc.perform(post("/api/v1/withdrawals").with(as(treasurer, BOTH))
                .contentType(MediaType.APPLICATION_JSON).content(body(project, amount, false)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private BigDecimal provisional(Project project) {
        return balanceService.projectBalance(project.getId()).provisionalBalance();
    }

    private List<AuditLog> audits(long id, String action) {
        return auditLogRepository.findAll().stream()
            .filter(a -> "WITHDRAWAL".equals(a.getEntityType()) && a.getEntityId() == id && action.equals(a.getAction()))
            .toList();
    }

    @Test
    void formListOffersOpenThenClosedWithBalanceButNotEmptyOrArchived() throws Exception {
        Project open = project("Sono de l'église", ProjectStatus.ACTIVE, "0");
        Project closedEmpty = project("Collecte soldée", ProjectStatus.COMPLETED, "0");
        Project archived = project("Ancienne collecte", ProjectStatus.ARCHIVED, "5000");

        String json = mockMvc.perform(get("/api/v1/withdrawals/projects").with(as(treasurer, BOTH)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(json);
        List<Long> ids = new ArrayList<>();
        list.forEach(n -> ids.add(n.get("id").asLong()));

        assertThat(ids).contains(open.getId(), closed.getId()).doesNotContain(closedEmpty.getId(), archived.getId());
        // Ouvertes d'abord, clôturées ensuite.
        assertThat(ids.indexOf(open.getId())).isLessThan(ids.indexOf(closed.getId()));
        boolean seenClosed = false;
        for (JsonNode n : list) {
            seenClosed |= "COMPLETED".equals(n.get("status").asText());
            if (seenClosed) {
                assertThat(n.get("status").asText()).isEqualTo("COMPLETED");
            }
        }
        JsonNode closedRow = list.get(ids.indexOf(closed.getId()));
        assertThat(closedRow.get("remainingBalance").decimalValue()).isEqualByComparingTo("17000");
        assertThat(list.get(ids.indexOf(open.getId())).get("remainingBalance").isNull()).isTrue();
    }

    @Test
    void recordedExitEqualToBalanceIsAcceptedAndProjectStaysClosed() throws Exception {
        String json = mockMvc.perform(post("/api/v1/withdrawals/record").with(as(treasurer, BOTH))
                .contentType(MediaType.APPLICATION_JSON).content(body(closed, "17000", true)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROCESSED"))
            .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        assertThat(provisional(closed)).isEqualByComparingTo("0");
        assertThat(projectRepository.findById(closed.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(audits(id, "RECORD")).singleElement()
            .satisfies(a -> assertThat(a.getSummary()).contains("enveloppe clôturée"));

        // Soldée : elle disparaît de la liste du formulaire.
        mockMvc.perform(get("/api/v1/withdrawals/projects").with(as(treasurer, BOTH)))
            .andExpect(jsonPath("$[?(@.id == " + closed.getId() + ")]").isEmpty());
    }

    @Test
    void exitAboveBalanceIsRefusedWith422() throws Exception {
        mockMvc.perform(post("/api/v1/withdrawals/record").with(as(treasurer, BOTH))
                .contentType(MediaType.APPLICATION_JSON).content(body(closed, "17001", true)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.rule").value("CLOSED_PROJECT_BALANCE"))
            .andExpect(jsonPath("$.message").value(
                "Cette collecte est clôturée : la sortie ne peut pas dépasser le solde restant (17 000 FCFA)."));

        mockMvc.perform(post("/api/v1/withdrawals").with(as(treasurer, BOTH))
                .contentType(MediaType.APPLICATION_JSON).content(body(closed, "20000", false)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.rule").value("CLOSED_PROJECT_BALANCE"));

        assertThat(provisional(closed)).isEqualByComparingTo("17000");
    }

    @Test
    void requestApproveProcessOnClosedProject() throws Exception {
        long id = createRequest(closed, "17000");
        assertThat(audits(id, "CREATE")).singleElement()
            .satisfies(a -> assertThat(a.getSummary()).contains("enveloppe clôturée"));
        assertThat(provisional(closed)).isEqualByComparingTo("0");

        // Une seconde demande ne passe plus : tout le reste est déjà engagé.
        mockMvc.perform(post("/api/v1/withdrawals").with(as(treasurer, BOTH))
                .contentType(MediaType.APPLICATION_JSON).content(body(closed, "1", false)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "Cette collecte est clôturée : la sortie ne peut pas dépasser le solde restant (0 FCFA)."));

        mockMvc.perform(post("/api/v1/withdrawals/" + id + "/approve").with(as(otherTreasurer, BOTH)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/v1/withdrawals/" + id + "/process").with(as(otherTreasurer, BOTH)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(balanceService.projectBalance(closed.getId()).currentBalance()).isEqualByComparingTo("0");
        assertThat(projectRepository.findById(closed.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(audits(id, "APPROVE")).singleElement()
            .satisfies(a -> assertThat(a.getSummary()).contains("enveloppe clôturée"));
        assertThat(audits(id, "PROCESS")).singleElement()
            .satisfies(a -> assertThat(a.getSummary()).contains("enveloppe clôturée"));
    }

    @Test
    void approveIsRefusedWhenTheClosedBalanceNoLongerCoversTheRequest() throws Exception {
        long id = createRequest(closed, "17000");
        // Le solde baisse entre la demande et la validation (correction du solde d'ouverture).
        Project p = projectRepository.findById(closed.getId()).orElseThrow();
        p.setOpeningBalance(new BigDecimal("10000"));
        projectRepository.saveAndFlush(p);

        mockMvc.perform(post("/api/v1/withdrawals/" + id + "/approve").with(as(otherTreasurer, BOTH)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "Cette collecte est clôturée : la sortie ne peut pas dépasser le solde restant (10 000 FCFA)."));
    }

    @Test
    void openProjectIsUnchangedAndMayStillGoIntoDeficit() throws Exception {
        Project open = project("Sono de l'église", ProjectStatus.ACTIVE, "1000");
        long id = createRequest(open, "5000");
        assertThat(provisional(open)).isEqualByComparingTo("-4000");
        assertThat(audits(id, "CREATE")).singleElement()
            .satisfies(a -> assertThat(a.getSummary()).doesNotContain("clôturée"));
        mockMvc.perform(post("/api/v1/withdrawals/" + id + "/approve").with(as(otherTreasurer, BOTH)))
            .andExpect(status().isOk());
    }
}
