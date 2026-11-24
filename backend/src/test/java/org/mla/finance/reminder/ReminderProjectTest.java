package org.mla.finance.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderResponseType;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ReminderCaseRepository;
import org.mla.finance.service.ContributionService;
import org.mla.finance.service.EmailService;
import org.mla.finance.web.dto.ContributionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relances d'une collecte (lot 2 bis, cadrage §6 bis) : non relançable par défaut, bloc « Relances » du projet,
 * ciblage, un don de n'importe quel montant clôt le dossier, plus de relance après l'échéance, désactivation ou clôture
 * de la collecte → dossiers « Sans suite », motifs de l'écran Relances, lien « Participer maintenant ».
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.reminders.jobs-enabled=false",
    "app.reminders.public-site-url=https://don.example.test",
    "app.payment.pawapay.poll-interval-ms=3600000",
    "app.payment.pawapay.poll-initial-delay-ms=3600000"
})
class ReminderProjectTest {

    /** 20/11/2026 à 10 h, heure de Douala. */
    private static final Instant NOV_20_10H = Instant.parse("2026-11-20T09:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReminderService reminderService;
    @Autowired ReminderPublicService publicService;
    @Autowired ReminderResponseService responseService;
    @Autowired ContributionService contributionService;
    @Autowired DonorRepository donorRepository;
    @Autowired ReminderCaseRepository caseRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @MockBean ReminderClock clock;
    @MockBean EmailService emailService;

    private final List<Long> createdDonors = new ArrayList<>();
    private final List<Long> createdProjects = new ArrayList<>();
    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOV_20_10H);
        when(emailService.sendHtml(anyString(), anyString(), anyString(), any()))
            .thenReturn(new EmailService.SendResult(true, "re_test", null));
        // Les autres membres actifs de la base de dev n'ont pas d'importance ici : chaque test vise ses membres.
    }

    @AfterEach
    void cleanUp() {
        if (!createdDonors.isEmpty()) {
            String ids = createdDonors.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbc.update("delete from reminder_responses where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
            jdbc.update("delete from reminder_whatsapp_replies where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
            jdbc.update("delete from access_tokens where donor_id in (" + ids + ")");
            jdbc.update("delete from reminder_deliveries where reminder_id in (select r.id from reminders r join reminder_cases c on r.case_id = c.id where c.donor_id in (" + ids + "))");
            jdbc.update("delete from reminders where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
            jdbc.update("delete from reminder_cases where donor_id in (" + ids + ")");
            jdbc.update("delete from contributions where donor_id in (" + ids + ")");
        }
        if (!createdProjects.isEmpty()) {
            String ids = createdProjects.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbc.update("delete from reminder_responses where case_id in (select id from reminder_cases where project_id in (" + ids + "))");
            jdbc.update("delete from reminder_whatsapp_replies where case_id in (select id from reminder_cases where project_id in (" + ids + "))");
            jdbc.update("delete from access_tokens where case_id in (select id from reminder_cases where project_id in (" + ids + "))");
            jdbc.update("delete from reminder_deliveries where reminder_id in (select r.id from reminders r join reminder_cases c on r.case_id = c.id where c.project_id in (" + ids + "))");
            jdbc.update("delete from reminders where case_id in (select id from reminder_cases where project_id in (" + ids + "))");
            jdbc.update("delete from reminder_cases where project_id in (" + ids + ")");
            jdbc.update("delete from contributions where project_id in (" + ids + ")");
            jdbc.update("delete from project_reminder_members where project_id in (" + ids + ")");
            jdbc.update("delete from audit_logs where entity_type = 'PROJECT' and entity_id in (" + ids + ")");
            jdbc.update("delete from projects where id in (" + ids + ")");
        }
        if (!createdDonors.isEmpty()) {
            jdbc.update("delete from donors where id in (" + createdDonors.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        }
    }

    private static RequestPostProcessor as(String... permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", List.of(permissions)))
            .authorities(List.of(permissions).stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    private Donor member(String name, MemberRole role) {
        Donor donor = donorRepository.save(Donor.builder().displayName(name + " " + tag).fullName(name + " Nom Complet")
            .email(name.toLowerCase() + "." + tag + "@example.test").memberRole(role).memberStatus(MemberStatus.ACTIVE).build());
        createdDonors.add(donor.getId());
        return donor;
    }

    /** Crée une collecte (projet) puis l'active, avec le bloc « Relances » donné (null : sans relances). */
    private long project(String name, Map<String, Object> reminders) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name + " " + tag);
        body.put("openingBalance", 0);
        body.put("startDate", "2026-11-01");
        body.put("endDate", "2026-12-05");
        if (reminders != null) {
            body.put("reminders", reminders);
        }
        String json = mockMvc.perform(post("/api/v1/projects").with(as("MANAGE_PROJECTS"))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(json).get("id").asLong();
        createdProjects.add(id);
        mockMvc.perform(post("/api/v1/projects/" + id + "/activate").with(as("MANAGE_PROJECTS"))).andExpect(status().isOk());
        return id;
    }

    private static Map<String, Object> reminders(boolean enabled, String audience, List<Long> memberIds) {
        Map<String, Object> r = new HashMap<>();
        r.put("enabled", enabled);
        r.put("audience", audience);
        r.put("message", "Mariage de Stanzi le 6 décembre");
        r.put("memberIds", memberIds);
        return r;
    }

    private JsonNode overview(long projectId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/reminders").param("projectId", String.valueOf(projectId)).with(as("SEND_REMINDERS")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private JsonNode row(JsonNode overview, Donor donor) {
        for (JsonNode row : overview.get("rows")) {
            if (row.get("donorId").asLong() == donor.getId()) {
                return row;
            }
        }
        return null;
    }

    private org.springframework.test.web.servlet.ResultActions remind(Donor donor, long projectId) throws Exception {
        return mockMvc.perform(post("/api/v1/reminders").with(as("SEND_REMINDERS")).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "projectId", projectId))));
    }

    private ReminderCase caseOf(Donor donor, long projectId) {
        return caseRepository.findByDonorIdAndReasonAndCaseKey(donor.getId(), ReminderReason.PROJECT, "P:" + projectId).orElseThrow();
    }

    private void donate(Donor donor, long projectId, int amount) {
        long cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
        contributionService.create(new ContributionRequest(projectId, null, donor.getId(), cash, BigDecimal.valueOf(amount),
            YearMonth.of(2026, 11), LocalDate.of(2026, 11, 20), false, "don test", MovementNature.DONATION, null, null));
    }

    @Test
    void aCollecteIsNotRemindableByDefault() throws Exception {
        Donor donor = member("Alice", MemberRole.MEMBRE);
        long projectId = project("Toiture", null);

        JsonNode row = row(overview(projectId), donor);
        assertThat(row.get("canRemind").asBoolean()).isFalse();
        assertThat(row.get("blockedReason").asText()).contains("désactivées");
        remind(donor, projectId).andExpect(status().isUnprocessableEntity());

        String reasons = mockMvc.perform(get("/api/v1/reminders/reasons").with(as("SEND_REMINDERS")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reasons).contains("Contribution mensuelle — novembre 2026").doesNotContain("Toiture " + tag);
    }

    @Test
    void sendsTheCollecteReminderWithoutAnyAmountAndADonationClosesIt() throws Exception {
        Donor donor = member("Bruno", MemberRole.MEMBRE);
        long projectId = project("Mariage", reminders(true, "ALL", null));

        String reasons = mockMvc.perform(get("/api/v1/reminders/reasons").with(as("SEND_REMINDERS")))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reasons).contains("Collecte — Mariage " + tag);

        JsonNode overview = overview(projectId);
        assertThat(overview.get("reason").asText()).isEqualTo("PROJECT");
        assertThat(overview.get("dueDate").asText()).isEqualTo("2026-12-05");
        JsonNode row = row(overview, donor);
        assertThat(row.get("canRemind").asBoolean()).isTrue();
        assertThat(row.get("expectedAmount").isNull()).isTrue();

        remind(donor, projectId).andExpect(status().isOk());
        verify(emailService).sendHtml(eq(donor.getEmail()), contains("Mariage " + tag),
            contains("Mariage de Stanzi le 6 d"), contains("(Mariage de Stanzi le 6 décembre), ouverte jusqu'au 05/12/2026"));
        ReminderCase rc = caseOf(donor, projectId);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.EN_ATTENTE_REPONSE);
        assertThat(rc.getPeriodMonth()).isNull();
        // Délai d'attente : pas de deuxième relance tout de suite.
        remind(donor, projectId).andExpect(status().isConflict());

        // Un don, quel que soit son montant, clôt le dossier (RL-08).
        donate(donor, projectId, 500);
        assertThat(caseOf(donor, projectId).getStatus()).isEqualTo(ReminderCaseStatus.REGLE);
        assertThat(caseOf(donor, projectId).getContribution()).isNotNull();
        assertThat(row(overview(projectId), donor).get("blockedCode").asText()).isEqualTo("PAID");
    }

    @Test
    void alreadyPaidButNothingFoundAsksForTheReferenceAndAlertsTheTreasurers() throws Exception {
        // « C'est déjà fait » sur une collecte sans don : l'alerte aux trésoriers plantait (dossier sans mois).
        Donor donor = member("Dana", MemberRole.MEMBRE);
        long projectId = project("Toiture", reminders(true, "ALL", null));
        remind(donor, projectId).andExpect(status().isOk());
        Long caseId = caseOf(donor, projectId).getId();

        ReminderResponseService.Outcome outcome = responseService.respond(caseId, ReminderChannel.WHATSAPP,
            ReminderResponseType.DONE, null, null, "C'est déjà fait", "wamid.done-" + tag).orElseThrow();

        assertThat(outcome.reply()).startsWith("Nous ne retrouvons pas encore votre don.");
        ReminderCase rc = caseOf(donor, projectId);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.DECLARE_NON_RETROUVE);
        assertThat(rc.isNeedsAttention()).isTrue();
    }

    @Test
    void respectsTheTargetedMembers() throws Exception {
        Donor responsable = member("Chantal", MemberRole.RESPONSABLE);
        Donor membre = member("Didier", MemberRole.MEMBRE);
        Donor chosen = member("Edith", MemberRole.MEMBRE);

        long forResponsables = project("Retraite", reminders(true, "RESPONSABLES", null));
        JsonNode overview = overview(forResponsables);
        assertThat(row(overview, responsable)).isNotNull();
        assertThat(row(overview, membre)).isNull();
        remind(membre, forResponsables).andExpect(status().isUnprocessableEntity());

        long forSelection = project("Visite", reminders(true, "SELECTION", List.of(chosen.getId())));
        overview = overview(forSelection);
        assertThat(row(overview, chosen)).isNotNull();
        assertThat(row(overview, membre)).isNull();
        assertThat(row(overview, responsable)).isNull();

        mockMvc.perform(post("/api/v1/projects").with(as("MANAGE_PROJECTS")).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Vide " + tag, "openingBalance", 0,
                    "endDate", "2026-12-05", "reminders", reminders(true, "SELECTION", List.of())))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresADueDateWhenTheCollecteHasNoEnd() throws Exception {
        mockMvc.perform(post("/api/v1/projects").with(as("MANAGE_PROJECTS")).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Sans fin " + tag, "openingBalance", 0,
                    "reminders", reminders(true, "ALL", null)))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void noReminderAfterTheDueDateAndOpenCasesAreClosedByTheDailyJob() throws Exception {
        Donor donor = member("Fabrice", MemberRole.MEMBRE);
        long projectId = project("Noël", reminders(true, "ALL", null));
        remind(donor, projectId).andExpect(status().isOk());

        when(clock.now()).thenReturn(Instant.parse("2026-12-06T09:00:00Z"));
        JsonNode row = row(overview(projectId), donor);
        assertThat(row.get("canRemind").asBoolean()).isFalse();
        assertThat(row.get("blockedReason").asText()).contains("Échéance de la collecte dépassée");

        reminderService.closeFinishedMonths();
        ReminderCase rc = caseOf(donor, projectId);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.SANS_SUITE);
        assertThat(rc.getClosedReason()).isEqualTo("Échéance de la collecte dépassée");
    }

    @Test
    void disablingTheRemindersOrClosingTheCollecteClosesOpenCases() throws Exception {
        Donor a = member("Gaston", MemberRole.MEMBRE);
        Donor b = member("Hélène", MemberRole.MEMBRE);
        long first = project("Chorale", reminders(true, "ALL", null));
        remind(a, first).andExpect(status().isOk());

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Chorale " + tag);
        update.put("startDate", "2026-11-01");
        update.put("endDate", "2026-12-05");
        update.put("reminders", reminders(false, "ALL", null));
        mockMvc.perform(put("/api/v1/projects/" + first).with(as("MANAGE_PROJECTS")).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update))).andExpect(status().isOk());
        assertThat(caseOf(a, first).getStatus()).isEqualTo(ReminderCaseStatus.SANS_SUITE);
        assertThat(caseOf(a, first).getClosedReason()).isEqualTo("Relances de la collecte désactivées");

        long second = project("Concert", reminders(true, "ALL", null));
        remind(b, second).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/" + second + "/complete").with(as("MANAGE_PROJECTS"))).andExpect(status().isOk());
        assertThat(caseOf(b, second).getStatus()).isEqualTo(ReminderCaseStatus.SANS_SUITE);
        assertThat(caseOf(b, second).getClosedReason()).isEqualTo("Collecte clôturée");
    }

    @Test
    void participateLinkOpensTheCollecteAndDateChoicesStopAtTheDueDate() throws Exception {
        Donor donor = member("Irène", MemberRole.MEMBRE);
        long projectId = project("Orphelinat", reminders(true, "ALL", null));
        remind(donor, projectId).andExpect(status().isOk());
        ReminderCase rc = caseOf(donor, projectId);

        ReminderLinks links = tx.execute(st -> reminderService.links(caseRepository.findById(rc.getId()).orElseThrow()));
        String contributeToken = links.contributeToken();
        ReminderPublicService.ContributionLink link = publicService.contributionLink(contributeToken);
        assertThat(link.reason()).isEqualTo("PROJECT");
        assertThat(link.projectId()).isEqualTo(projectId);
        assertThat(link.months()).isEmpty();
        assertThat(publicService.memberForProjectDonation(contributeToken, projectId)).map(Donor::getId).contains(donor.getId());
        assertThat(publicService.memberForProjectDonation(contributeToken, projectId + 999)).isEmpty();

        String actionToken = links.actionToken();
        ReminderPublicService.ActionResult ack = publicService.act(actionToken, "ACK", null);
        assertThat(ack.message()).contains("participer à la collecte");
        assertThat(ack.dateOptions()).isNotEmpty();
        assertThat(ack.dateOptions()).allMatch(o -> !o.date().isAfter(LocalDate.of(2026, 12, 5)));
        assertThat(ack.dateOptions().get(ack.dateOptions().size() - 1).date()).isEqualTo(LocalDate.of(2026, 12, 5));

        ReminderPublicService.ActionResult promised = publicService.act(actionToken, "PROMISE", LocalDate.of(2026, 12, 2));
        assertThat(promised.status()).isEqualTo("PROMIS");
    }
}
