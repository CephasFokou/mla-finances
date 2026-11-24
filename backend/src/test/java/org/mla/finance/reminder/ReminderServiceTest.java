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
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionKind;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relances de la contribution mensuelle (lot 1) : éligibilité, délais d'attente, maximum, plage horaire et file,
 * clôture automatique par une contribution (RL-08, après commit), relance groupée, concurrence, permission.
 * Sans transaction de test (le listener RL-08 s'exécute après commit) : les données créées sont supprimées après
 * chaque test. Horloge et envoi d'email simulés.
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
class ReminderServiceTest {

    /** 20/11/2026 à 10 h, heure de Douala (UTC+1). */
    private static final Instant NOV_20_10H = Instant.parse("2026-11-20T09:00:00Z");
    private static final String MONTH = "2026-11";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReminderService reminderService;
    @Autowired ContributionService contributionService;
    @Autowired DonorRepository donorRepository;
    @Autowired ReminderCaseRepository caseRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired JdbcTemplate jdbc;
    @MockBean ReminderClock clock;
    @MockBean EmailService emailService;

    private final List<Long> createdDonors = new ArrayList<>();
    private final String tag = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOV_20_10H);
        when(emailService.sendHtml(anyString(), anyString(), anyString(), any()))
            .thenReturn(new EmailService.SendResult(true, "re_test", null));
    }

    @AfterEach
    void cleanUp() {
        if (createdDonors.isEmpty()) {
            return;
        }
        String ids = createdDonors.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbc.update("delete from reminder_responses where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
        jdbc.update("delete from reminder_whatsapp_replies where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
        jdbc.update("delete from access_tokens where donor_id in (" + ids + ")");
        jdbc.update("delete from reminder_deliveries where reminder_id in (select r.id from reminders r join reminder_cases c on r.case_id = c.id where c.donor_id in (" + ids + "))");
        jdbc.update("delete from reminders where case_id in (select id from reminder_cases where donor_id in (" + ids + "))");
        jdbc.update("delete from reminder_cases where donor_id in (" + ids + ")");
        jdbc.update("delete from contributions where donor_id in (" + ids + ") and adjusts_contribution_id is not null");
        jdbc.update("delete from contributions where donor_id in (" + ids + ")");
        jdbc.update("delete from payment_transaction_months where payment_transaction_id in (select id from payment_transactions where donor_id in (" + ids + "))");
        jdbc.update("delete from payment_transactions where donor_id in (" + ids + ")");
        jdbc.update("delete from donors where id in (" + ids + ")");
    }

    private static RequestPostProcessor as(String... permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", List.of(permissions)))
            .authorities(List.of(permissions).stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    private Donor member(String name, MemberRole role, MemberStatus status, String email) {
        Donor donor = donorRepository.save(Donor.builder().displayName(name + " " + tag).fullName(name + " Nom Complet")
            .email(email).memberRole(role).memberStatus(status).build());
        createdDonors.add(donor.getId());
        return donor;
    }

    private Donor activeMember(String name) {
        return member(name, MemberRole.MEMBRE, MemberStatus.ACTIVE, name.toLowerCase().replace(' ', '.') + "@example.test");
    }

    private JsonNode send(Donor donor, int expectedStatus) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "month", MONTH));
        String content = mockMvc.perform(post("/api/v1/reminders").with(as("SEND_REMINDERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    private JsonNode rowOf(Donor donor) throws Exception {
        String content = mockMvc.perform(get("/api/v1/reminders").param("month", MONTH).with(as("SEND_REMINDERS")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode row : objectMapper.readTree(content).get("rows")) {
            if (row.get("donorId").asLong() == donor.getId()) {
                return row;
            }
        }
        return null;
    }

    private ReminderCase caseOf(Donor donor) {
        return caseRepository.findByDonorIdAndReasonAndCaseKey(donor.getId(), ReminderReason.MONTHLY_CONTRIBUTION,
            ReminderCase.monthKey(LocalDate.of(2026, 11, 1))).orElseThrow();
    }

    private void recordMonthlyContribution(Donor donor) {
        long cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
        long caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow().getId();
        contributionService.create(new ContributionRequest(null, caisse, donor.getId(), cash, BigDecimal.valueOf(2000),
            YearMonth.of(2026, 11), LocalDate.of(2026, 11, 20), false, "test relances", MovementNature.MONTHLY_CONTRIBUTION,
            null, null));
    }

    @Test
    void sendsEmailAboutTheTargetMonthOnlyThenWaitsThreeDays() throws Exception {
        Donor donor = activeMember("Rel Envoi");

        JsonNode result = send(donor, 200);
        assertThat(result.get("sent").asBoolean()).isTrue();
        assertThat(result.get("queued").asBoolean()).isFalse();
        assertThat(result.get("deliveries").get(0).get("recipient").asText()).isEqualTo("r•••@example.test");

        verify(emailService).sendHtml(eq(donor.getEmail()), eq("Rappel — votre contribution MLA de novembre 2026"),
            anyString(), any());
        ReminderCase rc = caseOf(donor);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.EN_ATTENTE_REPONSE);
        assertThat(rc.getReminderCount()).isEqualTo(1);
        assertThat(rc.getNextAllowedAt()).isEqualTo(NOV_20_10H.plus(Duration.ofDays(3)));

        // La relance ne cite que le mois visé (jamais les mois en retard), avec le montant habituel et l'échéance.
        JsonNode preview = objectMapper.readTree(mockMvc.perform(get("/api/v1/reminders/preview")
                .param("donorId", donor.getId().toString()).param("month", MONTH).with(as("SEND_REMINDERS")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String text = preview.get("text").asText();
        assertThat(text).contains("novembre 2026", "2 000 FCFA", "30/11/2026", "https://don.example.test/");
        assertThat(text).doesNotContain("octobre", "septembre", "décembre");

        // Délai d'attente : 409 avec le motif, puis de nouveau possible après 3 jours.
        JsonNode conflict = send(donor, 409);
        assertThat(conflict.get("message").asText()).contains("relance possible à partir du 23/11 à 10:00");
        JsonNode row = rowOf(donor);
        assertThat(row.get("canRemind").asBoolean()).isFalse();
        assertThat(row.get("blockedCode").asText()).isEqualTo("COOLDOWN");

        when(clock.now()).thenReturn(NOV_20_10H.plus(Duration.ofDays(3)));
        send(donor, 200);
        assertThat(caseOf(donor).getReminderCount()).isEqualTo(2);
    }

    @Test
    void stopsAfterThreeRemindersInTheMonth() throws Exception {
        Donor donor = activeMember("Rel Maximum");
        for (int i = 0; i < 3; i++) {
            when(clock.now()).thenReturn(NOV_20_10H.plus(Duration.ofDays(3L * i)));
            send(donor, 200);
        }
        when(clock.now()).thenReturn(NOV_20_10H.plus(Duration.ofDays(9)));
        JsonNode refused = send(donor, 422);
        assertThat(refused.get("message").asText()).contains("Maximum de 3 relances");
        verify(emailService, times(3)).sendHtml(anyString(), anyString(), anyString(), any());
    }

    @Test
    void refusesMembersWhoCannotBeReminded() throws Exception {
        Donor paid = activeMember("Rel Paye");
        recordMonthlyContribution(paid);
        assertThat(send(paid, 422).get("message").asText()).contains("déjà enregistrée");

        Donor left = member("Rel Parti", MemberRole.MEMBRE, MemberStatus.LEFT, "parti@example.test");
        send(left, 422);

        Donor noEmail = member("Rel Sans Email", MemberRole.MEMBRE, MemberStatus.ACTIVE, null);
        noEmail.setPhone("237690000001");
        donorRepository.save(noEmail);
        assertThat(send(noEmail, 422).get("message").asText()).contains("Aucun email");
        assertThat(rowOf(noEmail).get("blockedCode").asText()).isEqualTo("NO_CONTACT");

        Donor early = activeMember("Rel Trop Tot");
        when(clock.now()).thenReturn(Instant.parse("2026-11-09T09:00:00Z"));
        assertThat(send(early, 422).get("message").asText()).contains("à partir du 10/11/2026");

        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString(), any());
    }

    @Test
    void paymentInProgressBlocksTheReminder() throws Exception {
        Donor donor = activeMember("Rel En Cours");
        PaymentTransaction tx = PaymentTransaction.builder()
            .kind(PaymentTransactionKind.MONTHLY_CONTRIBUTION)
            .donor(donor)
            .category(categoryRepository.findFirstByMainFundTrue().orElseThrow())
            .paymentChannel(paymentChannelRepository.findByCode("ORANGE_MONEY").orElseThrow())
            .amount(BigDecimal.valueOf(2000))
            .amountPerMonth(BigDecimal.valueOf(2000))
            .coveredMonths(new ArrayList<>(List.of(LocalDate.of(2026, 11, 1))))
            .status(PaymentTransactionStatus.PROCESSING)
            .reference("MLA-T" + tag)
            .build();
        paymentTransactionRepository.save(tx);

        assertThat(send(donor, 409).get("message").asText()).contains("en cours de validation");
        assertThat(rowOf(donor).get("monthStatus").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void contributionRecordedAfterTheReminderClosesTheCaseAsSettled() throws Exception {
        Donor donor = activeMember("Rel Regle");
        send(donor, 200);

        recordMonthlyContribution(donor);

        ReminderCase rc = caseOf(donor);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.REGLE);
        assertThat(rc.getContribution()).isNotNull();
        JsonNode row = rowOf(donor);
        assertThat(row.get("monthStatus").asText()).isEqualTo("PAID");
        assertThat(row.get("caseStatus").asText()).isEqualTo("REGLE");
        assertThat(row.get("canRemind").asBoolean()).isFalse();
    }

    @Test
    void outsideTheSendingWindowTheReminderIsQueuedThenSentAtEightOrCancelledIfPaid() throws Exception {
        Donor waiting = activeMember("Rel File");
        Donor payer = activeMember("Rel File Paye");
        when(clock.now()).thenReturn(Instant.parse("2026-11-20T20:30:00Z")); // 21 h 30 à Douala

        JsonNode queued = send(waiting, 200);
        assertThat(queued.get("queued").asBoolean()).isTrue();
        assertThat(queued.get("scheduledFor").asText()).isEqualTo("2026-11-21T07:00:00Z"); // 8 h à Douala
        send(payer, 200);
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString(), any());
        assertThat(rowOf(waiting).get("blockedCode").asText()).isEqualTo("QUEUED");

        recordMonthlyContribution(payer);

        when(clock.now()).thenReturn(Instant.parse("2026-11-21T07:00:30Z"));
        reminderService.sendDueQueued();

        verify(emailService, times(1)).sendHtml(eq(waiting.getEmail()), anyString(), anyString(), any());
        verify(emailService, never()).sendHtml(eq(payer.getEmail()), anyString(), anyString(), any());
        assertThat(caseOf(waiting).getNextAllowedAt()).isEqualTo(Instant.parse("2026-11-24T07:00:30Z"));
        ReminderCase paidCase = caseOf(payer);
        assertThat(paidCase.getStatus()).isEqualTo(ReminderCaseStatus.REGLE);
        assertThat(paidCase.getReminderCount()).isZero();
    }

    @Test
    void failedEmailIsTracedButDoesNotCountNorBlock() throws Exception {
        Donor donor = activeMember("Rel Echec");
        when(emailService.sendHtml(anyString(), anyString(), anyString(), any()))
            .thenReturn(new EmailService.SendResult(false, null, "Envoi d'email non configuré (RESEND_API_KEY absente)."));

        JsonNode result = send(donor, 200);
        assertThat(result.get("sent").asBoolean()).isFalse();
        assertThat(result.get("deliveries").get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(caseOf(donor).getReminderCount()).isZero();

        when(emailService.sendHtml(anyString(), anyString(), anyString(), any()))
            .thenReturn(new EmailService.SendResult(true, "re_ok", null));
        assertThat(send(donor, 200).get("sent").asBoolean()).isTrue();
        assertThat(caseOf(donor).getReminderCount()).isEqualTo(1);
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/reminders/cases/" + caseOf(donor).getId())
                .with(as("SEND_REMINDERS"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(detail.get("reminders")).hasSize(2);
    }

    @Test
    void bulkRemindsEligibleMembersAndListsTheOthersWithTheirReason() throws Exception {
        Donor a = activeMember("Rel Groupe A");
        Donor b = activeMember("Rel Groupe B");
        Donor paid = activeMember("Rel Groupe Paye");
        recordMonthlyContribution(paid);

        String body = objectMapper.writeValueAsString(Map.of("donorIds", List.of(a.getId(), b.getId(), paid.getId()), "month", MONTH));
        JsonNode report = objectMapper.readTree(mockMvc.perform(post("/api/v1/reminders/bulk").with(as("SEND_REMINDERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(report.get("sent")).hasSize(2);
        assertThat(report.get("skipped")).hasSize(1);
        assertThat(report.get("skipped").get(0).get("donorId").asLong()).isEqualTo(paid.getId());
        assertThat(report.get("skipped").get(0).get("reason").asText()).contains("déjà enregistrée");
    }

    @Test
    void twoSimultaneousClicksSendASingleReminder() throws Exception {
        Donor donor = activeMember("Rel Concurrence");
        clearInvocations(emailService);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    String body = objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "month", MONTH));
                    return mockMvc.perform(post("/api/v1/reminders").with(as("SEND_REMINDERS"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        verify(emailService, times(1)).sendHtml(anyString(), anyString(), anyString(), any());
        assertThat(caseOf(donor).getReminderCount()).isEqualTo(1);
    }

    @Test
    void manualCloseStopsRemindersForTheMonth() throws Exception {
        Donor donor = activeMember("Rel Dispense");
        String body = objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "month", MONTH, "reason", "Dispensé ce mois"));
        mockMvc.perform(post("/api/v1/reminders/close").with(as("SEND_REMINDERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        assertThat(caseOf(donor).getStatus()).isEqualTo(ReminderCaseStatus.SANS_SUITE);
        assertThat(send(donor, 422).get("message").asText()).contains("Dispensé ce mois");
    }

    @Test
    void finishedMonthIsReadOnlyAndItsOpenCasesAreClosed() throws Exception {
        Donor donor = activeMember("Rel Mois Fini");
        send(donor, 200);

        when(clock.now()).thenReturn(Instant.parse("2026-12-01T00:30:00Z"));
        send(donor, 422);
        reminderService.closeFinishedMonths();
        assertThat(caseOf(donor).getStatus()).isEqualTo(ReminderCaseStatus.SANS_SUITE);
    }

    @Test
    void requiresTheSendRemindersPermission() throws Exception {
        Donor donor = activeMember("Rel Permission");
        String body = objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "month", MONTH));
        mockMvc.perform(post("/api/v1/reminders").with(as("MANAGE_CONTRIBUTIONS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reminders").with(as("VIEW_DASHBOARD"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/reminders").with(as("SEND_REMINDERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }
}
