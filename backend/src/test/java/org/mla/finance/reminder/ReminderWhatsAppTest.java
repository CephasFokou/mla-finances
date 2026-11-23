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
import org.mla.finance.domain.ReminderDeliveryStatus;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.domain.ReminderResponseType;
import org.mla.finance.reminder.whatsapp.KapsoClient;
import org.mla.finance.reminder.whatsapp.WhatsAppChannel;
import org.mla.finance.reminder.whatsapp.WhatsAppTemplate;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ReminderCaseRepository;
import org.mla.finance.repository.ReminderDeliveryRepository;
import org.mla.finance.repository.ReminderResponseRepository;
import org.mla.finance.service.ContributionService;
import org.mla.finance.service.EmailService;
import org.mla.finance.web.dto.ContributionRequest;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relances, lot 2 : envoi WhatsApp (modèle approuvé), webhook Kapso (signature, idempotence, routage avec le suivi des
 * tâches), réponses des membres (bouton, liste de dates, texte libre, référence), rappel à la date promise, page de
 * réponse des emails et lien « Contribuer maintenant ». Kapso et Resend simulés ; horloge fixe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.reminders.jobs-enabled=false",
    "app.reminders.public-site-url=https://don.example.test",
    "app.kapso.enabled=true",
    "app.kapso.api-key=test-key",
    "app.kapso.phone-number-id=123456",
    "app.kapso.webhook-secret=whsec-test",
    "app.payment.pawapay.poll-interval-ms=3600000",
    "app.payment.pawapay.poll-initial-delay-ms=3600000",
    "app.rate-limit.public-requests-per-minute=10000",
    "app.rate-limit.webhook-requests-per-minute=10000"
})
class ReminderWhatsAppTest {

    /** Vendredi 20/11/2026 à 10 h à Douala. */
    private static final Instant NOV_20_10H = Instant.parse("2026-11-20T09:00:00Z");
    private static final String MONTH = "2026-11";
    private static final String SECRET = "whsec-test";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReminderService reminderService;
    @Autowired ContributionService contributionService;
    @Autowired DonorRepository donorRepository;
    @Autowired ReminderCaseRepository caseRepository;
    @Autowired ReminderDeliveryRepository deliveryRepository;
    @Autowired ReminderResponseRepository responseRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired JdbcTemplate jdbc;
    @MockBean ReminderClock clock;
    @MockBean EmailService emailService;
    @MockBean KapsoClient kapso;

    private final List<Long> createdDonors = new ArrayList<>();
    private final String tag = UUID.randomUUID().toString().substring(0, 6);
    private final AtomicInteger wamids = new AtomicInteger();
    private final AtomicInteger phones = new AtomicInteger(10);

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOV_20_10H);
        when(emailService.sendHtml(anyString(), anyString(), anyString(), any()))
            .thenReturn(new EmailService.SendResult(true, "re_test", null));
        when(kapso.sendTemplate(anyString(), any())).thenAnswer(i -> new KapsoClient.Result(true, "wamid.out" + wamids.incrementAndGet(), null));
        when(kapso.sendText(anyString(), anyString())).thenReturn(new KapsoClient.Result(true, "wamid.txt", null));
        when(kapso.sendList(anyString(), anyString(), anyString(), anyList())).thenReturn(new KapsoClient.Result(true, "wamid.list", null));
    }

    @AfterEach
    void cleanUp() {
        if (createdDonors.isEmpty()) {
            return;
        }
        String ids = createdDonors.stream().map(String::valueOf).collect(Collectors.joining(","));
        String cases = "select id from reminder_cases where donor_id in (" + ids + ")";
        jdbc.update("delete from reminder_responses where case_id in (" + cases + ")");
        jdbc.update("delete from access_tokens where donor_id in (" + ids + ")");
        jdbc.update("delete from reminder_deliveries where reminder_id in (select id from reminders where case_id in (" + cases + "))");
        jdbc.update("delete from reminders where case_id in (" + cases + ")");
        jdbc.update("delete from reminder_cases where donor_id in (" + ids + ")");
        jdbc.update("delete from contributions where donor_id in (" + ids + ") and adjusts_contribution_id is not null");
        jdbc.update("delete from contributions where donor_id in (" + ids + ")");
        jdbc.update("delete from payment_transaction_months where payment_transaction_id in (select id from payment_transactions where donor_id in (" + ids + "))");
        jdbc.update("delete from payment_transactions where donor_id in (" + ids + ")");
        jdbc.update("delete from donors where id in (" + ids + ")");
    }

    // ------------------------------------------------------------------------------------------------ outils

    private static RequestPostProcessor as(String... permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", List.of(permissions)))
            .authorities(List.of(permissions).stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    private Donor member(String name) {
        String phone = "2376990" + String.format("%05d", Math.abs((tag + name).hashCode()) % 100000);
        Donor donor = donorRepository.save(Donor.builder().displayName(name + " " + tag).fullName(name + " Nom")
            .email(name.toLowerCase().replace(' ', '.') + "@example.test").phone(phone)
            .memberRole(MemberRole.MEMBRE).memberStatus(MemberStatus.ACTIVE).build());
        createdDonors.add(donor.getId());
        return donor;
    }

    private JsonNode send(Donor donor) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("donorId", donor.getId(), "month", MONTH));
        return json(mockMvc.perform(post("/api/v1/reminders").with(as("SEND_REMINDERS"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode json(String s) throws Exception {
        return objectMapper.readTree(s);
    }

    private ReminderCase caseOf(Donor donor) {
        return caseRepository.findByDonorIdAndReasonAndCaseKey(donor.getId(), ReminderReason.MONTHLY_CONTRIBUTION,
            ReminderCase.monthKey(LocalDate.of(2026, 11, 1))).orElseThrow();
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private int webhook(String event, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        return mockMvc.perform(post("/api/v1/webhooks/kapso").contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Event", event).header("X-Webhook-Signature", sign(body)).content(body))
            .andReturn().getResponse().getStatus();
    }

    private Map<String, Object> inbound(Donor donor, String wamid, Map<String, Object> messageFields) {
        Map<String, Object> message = new java.util.HashMap<>(Map.of("id", wamid, "timestamp", "1795000000",
            "kapso", Map.of("direction", "inbound", "status", "received")));
        message.putAll(messageFields);
        return Map.of("message", message, "conversation", Map.of("phone_number", "+" + donor.getPhone()),
            "phone_number_id", "123456");
    }

    private Map<String, Object> button(Donor donor, String wamid, String payload, String text) {
        return inbound(donor, wamid, Map.of("type", "button", "button", Map.of("payload", payload, "text", text)));
    }

    private Map<String, Object> textMessage(Donor donor, String wamid, String text) {
        return inbound(donor, wamid, Map.of("type", "text", "text", Map.of("body", text)));
    }

    private void payMonth(Donor donor) {
        long cash = paymentChannelRepository.findByCode("CASH").orElseThrow().getId();
        long caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow().getId();
        contributionService.create(new ContributionRequest(null, caisse, donor.getId(), cash, BigDecimal.valueOf(2000),
            YearMonth.of(2026, 11), LocalDate.of(2026, 11, 20), false, "test", MovementNature.MONTHLY_CONTRIBUTION, null, null));
    }

    // ------------------------------------------------------------------------------------------------ envoi

    @Test
    void reminderGoesOutOnWhatsAppWithTheApprovedTemplateAndOnEmailWithSignedLinks() throws Exception {
        Donor donor = member("Wa Envoi");
        JsonNode result = send(donor);
        assertThat(result.get("deliveries")).hasSize(2);

        ArgumentCaptor<WhatsAppTemplate> template = ArgumentCaptor.forClass(WhatsAppTemplate.class);
        verify(kapso).sendTemplate(eq(donor.getPhone()), template.capture());
        Long caseId = caseOf(donor).getId();
        assertThat(template.getValue().name()).isEqualTo("mla_finance_relance_contribution");
        assertThat(template.getValue().bodyParams()).containsEntry("mois", "novembre 2026").containsEntry("montant", "2 000 FCFA")
            .containsEntry("echeance", "30/11/2026");
        assertThat(template.getValue().quickReplyPayloads()).containsExactly("mlaf:ack:" + caseId, "mlaf:done:" + caseId);
        String contributeToken = template.getValue().urlSuffix();
        assertThat(contributeToken).matches("[A-Za-z0-9_-]{16,}");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtml(eq(donor.getEmail()), anyString(), html.capture(), any());
        assertThat(html.getValue()).contains("https://don.example.test/c/", "https://don.example.test/relance/", "Bien reçu",
            "C'est déjà fait");

        // Le lien « Contribuer maintenant » présélectionne le membre et le mois, sans identifiant interne dans l'URL.
        JsonNode link = json(mockMvc.perform(get("/api/v1/public/contribution-link/" + contributeToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(link.get("donorId").asLong()).isEqualTo(donor.getId());
        assertThat(link.get("months").get(0).asText()).isEqualTo(MONTH);
        mockMvc.perform(get("/api/v1/public/contribution-link/inconnu-inconnu-inconnu")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ webhook

    @Test
    void webhookRejectsAMissingOrWrongSignature() throws Exception {
        String body = "{\"message\":{\"id\":\"wamid.x\"}}";
        mockMvc.perform(post("/api/v1/webhooks/kapso").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/kapso").contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Signature", "00".repeat(32)).content(body))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/kapso").contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Signature", sign(body)).content(body))
            .andExpect(status().isOk());
    }

    @Test
    void signatureComputedWithAnEscapedApostropheIsAccepted() throws Exception {
        // Kapso a refusé « C'est déjà fait » : signature calculée sur \u0027, corps envoyé avec l'apostrophe en clair.
        Donor donor = member("Wa Apostrophe");
        send(donor);
        Long caseId = caseOf(donor).getId();
        String body = objectMapper.writeValueAsString(
            button(donor, "wamid.in-apos-" + tag, "mlaf:done:" + caseId, "C'est déjà fait"));
        assertThat(body).contains("C'est");
        String signedOn = body.replace("'", "\\u0027");

        int status = mockMvc.perform(post("/api/v1/webhooks/kapso").contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Event", "whatsapp.message.received").header("X-Webhook-Signature", sign(signedOn))
                .content(body))
            .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        assertThat(caseOf(donor).getStatus()).isEqualTo(ReminderCaseStatus.DECLARE_NON_RETROUVE);
        verify(kapso).sendText(eq(donor.getPhone()), contains("Nous ne retrouvons pas encore"));
    }

    @Test
    void ackButtonAsksForADateOnceEvenIfTheWebhookIsReplayed() throws Exception {
        Donor donor = member("Wa Accuse");
        send(donor);
        Long caseId = caseOf(donor).getId();

        for (int i = 0; i < 2; i++) {
            assertThat(webhook("whatsapp.message.received", button(donor, "wamid.in-ack-" + tag, "mlaf:ack:" + caseId, "Bien reçu")))
                .isEqualTo(200);
        }

        ReminderCase rc = caseOf(donor);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.ACCUSE);
        assertThat(rc.getNextAllowedAt()).isEqualTo(NOV_20_10H.plus(Duration.ofDays(5)));
        assertThat(responseRepository.findByReminderCaseIdOrderByReceivedAtAsc(caseId)).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KapsoClient.ListRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(kapso, times(1)).sendList(eq(donor.getPhone()), contains("Quand pensez-vous"), eq("Choisir une date"), rows.capture());
        // Vendredi 27/11, vendredi 27/11 + 7 j = hors mois ; le 25 ; fin du mois ; « Je ne sais pas encore ».
        assertThat(rows.getValue()).extracting(KapsoClient.ListRow::id).containsExactly(
            "mlaf:date:" + caseId + ":2026-11-25", "mlaf:date:" + caseId + ":2026-11-27",
            "mlaf:date:" + caseId + ":2026-11-30", "mlaf:unsure:" + caseId);
        assertThat(rows.getValue()).allSatisfy(r -> assertThat(r.title().length()).isLessThanOrEqualTo(24));
    }

    @Test
    void replyFromALegacyEightDigitCameroonianNumberIsRecognised() throws Exception {
        // WhatsApp identifie encore certains comptes par l'ancien numéro : 237 + 8 chiffres, sans le 6 initial.
        Donor donor = member("Wa Ancien");
        send(donor);
        Long caseId = caseOf(donor).getId();
        String legacy = "237" + donor.getPhone().substring(4);
        Map<String, Object> payload = Map.of(
            "message", Map.of("id", "wamid.in-legacy-" + tag, "from", legacy, "type", "button",
                "button", Map.of("payload", "mlaf:ack:" + caseId, "text", "Bien reçu"),
                "kapso", Map.of("direction", "inbound", "status", "received")),
            "conversation", Map.of("phone_number", legacy), "phone_number_id", "123456");

        assertThat(webhook("whatsapp.message.received", payload)).isEqualTo(200);

        assertThat(caseOf(donor).getStatus()).isEqualTo(ReminderCaseStatus.ACCUSE);
        verify(kapso).sendList(eq(donor.getPhone()), contains("Quand pensez-vous"), eq("Choisir une date"), any());
    }

    @Test
    void legacyEightDigitNumbersMatchTheCurrentFormat() {
        assertThat(WhatsAppChannel.normalize("23799123456")).isEqualTo("237699123456");
        assertThat(WhatsAppChannel.normalize("+237 6 99 12 34 56")).isEqualTo("237699123456");
        assertThat(WhatsAppChannel.sameNumber("699123456", "23799123456")).isTrue();
        assertThat(WhatsAppChannel.sameNumber("699123456", "23799123457")).isFalse();
    }

    @Test
    void chosenDateSchedulesAReminderAtNineThatDayUnlessThePaymentArrives() throws Exception {
        Donor waiting = member("Wa Promesse");
        Donor payer = member("Wa Promesse Paye");
        send(waiting);
        send(payer);
        Long caseId = caseOf(waiting).getId();
        Long payerCase = caseOf(payer).getId();

        webhook("whatsapp.message.received", inbound(waiting, "wamid.in-date-" + tag, Map.of("type", "interactive",
            "interactive", Map.of("type", "list_reply", "list_reply", Map.of("id", "mlaf:date:" + caseId + ":2026-11-25", "title", "Le 25/11")))));
        webhook("whatsapp.message.received", inbound(payer, "wamid.in-date2-" + tag, Map.of("type", "interactive",
            "interactive", Map.of("type", "list_reply", "list_reply", Map.of("id", "mlaf:date:" + payerCase + ":2026-11-25", "title", "Le 25/11")))));

        ReminderCase rc = caseOf(waiting);
        assertThat(rc.getStatus()).isEqualTo(ReminderCaseStatus.PROMIS);
        assertThat(rc.getPromisedDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(rc.getReminderCount()).isEqualTo(2); // le rappel programmé compte dans le maximum
        verify(kapso).sendText(eq(waiting.getPhone()), contains("le 25/11"));
        // Bouton de l'admin : débloqué le lendemain de la date promise.
        assertThat(rc.getNextAllowedAt()).isEqualTo(Instant.parse("2026-11-25T23:00:00Z"));

        payMonth(payer);

        when(clock.now()).thenReturn(Instant.parse("2026-11-25T08:00:30Z")); // 9 h à Douala
        reminderService.sendDueQueued();
        ArgumentCaptor<WhatsAppTemplate> template = ArgumentCaptor.forClass(WhatsAppTemplate.class);
        verify(kapso, times(2)).sendTemplate(eq(waiting.getPhone()), template.capture());
        assertThat(template.getValue().name()).isEqualTo("mla_finance_rappel_promesse");
        assertThat(template.getValue().quickReplyPayloads()).containsExactly("mlaf:done:" + caseId);
        verify(kapso, times(1)).sendTemplate(eq(payer.getPhone()), any());
        assertThat(caseOf(payer).getStatus()).isEqualTo(ReminderCaseStatus.REGLE);
    }

    @Test
    void doneIsAlwaysCheckedAgainstTheBooks() throws Exception {
        Donor paid = member("Wa Fait Paye");
        Donor pending = member("Wa Fait En Cours");
        Donor missing = member("Wa Fait Absent");
        for (Donor d : List.of(paid, pending, missing)) {
            send(d);
        }
        payMonth(paid);
        paymentTransactionRepository.save(PaymentTransaction.builder()
            .kind(PaymentTransactionKind.MONTHLY_CONTRIBUTION).donor(pending)
            .category(categoryRepository.findFirstByMainFundTrue().orElseThrow())
            .paymentChannel(paymentChannelRepository.findByCode("ORANGE_MONEY").orElseThrow())
            .amount(BigDecimal.valueOf(2000)).amountPerMonth(BigDecimal.valueOf(2000))
            .coveredMonths(new ArrayList<>(List.of(LocalDate.of(2026, 11, 1))))
            .status(PaymentTransactionStatus.PROCESSING).reference("MLA-W" + tag).build());

        webhook("whatsapp.message.received", button(pending, "wamid.d2-" + tag, "mlaf:done:" + caseOf(pending).getId(), "C'est déjà fait"));
        webhook("whatsapp.message.received", button(missing, "wamid.d3-" + tag, "mlaf:done:" + caseOf(missing).getId(), "C'est déjà fait"));

        assertThat(caseOf(paid).getStatus()).isEqualTo(ReminderCaseStatus.REGLE);
        assertThat(caseOf(pending).getStatus()).isEqualTo(ReminderCaseStatus.VERIFICATION);
        verify(kapso).sendText(eq(pending.getPhone()), contains("en cours de validation"));
        ReminderCase absent = caseOf(missing);
        assertThat(absent.getStatus()).isEqualTo(ReminderCaseStatus.DECLARE_NON_RETROUVE);
        assertThat(absent.isNeedsAttention()).isTrue();
        assertThat(absent.getNextAllowedAt()).isEqualTo(NOV_20_10H.plus(Duration.ofHours(48)));
        verify(kapso).sendText(eq(missing.getPhone()), contains("référence de paiement"));
        verify(emailService, atLeastOnce()).sendHtml(eq("admin@mla-finance.dev"), contains("dit avoir payé"), anyString(), any());

        // Le paiement en cours échoue : le dossier redevient relançable tout de suite.
        eventPublisher.publishEvent(new PaymentAbandonedEvent(pending.getId(), List.of(LocalDate.of(2026, 11, 1)), null));
        ReminderCase reopened = caseOf(pending);
        assertThat(reopened.getStatus()).isEqualTo(ReminderCaseStatus.EN_ATTENTE_REPONSE);
        assertThat(reopened.getNextAllowedAt()).isEqualTo(NOV_20_10H);
    }

    @Test
    void freeTextIsReadAndAReferenceIsChecked() throws Exception {
        Donor ok = member("Wa Texte Ok");
        Donor ref = member("Wa Texte Ref");
        Donor other = member("Wa Texte Autre");
        for (Donor d : List.of(ok, ref, other)) {
            send(d);
        }
        PaymentTransaction tx = paymentTransactionRepository.save(PaymentTransaction.builder()
            .kind(PaymentTransactionKind.MONTHLY_CONTRIBUTION).donor(ref)
            .category(categoryRepository.findFirstByMainFundTrue().orElseThrow())
            .paymentChannel(paymentChannelRepository.findByCode("ORANGE_MONEY").orElseThrow())
            .amount(BigDecimal.valueOf(2000)).amountPerMonth(BigDecimal.valueOf(2000))
            .coveredMonths(new ArrayList<>(List.of(LocalDate.of(2026, 11, 1))))
            .status(PaymentTransactionStatus.PROCESSING).reference("MLA-" + String.format("%06d", Math.abs(tag.hashCode()) % 1000000)).build());

        webhook("whatsapp.message.received", textMessage(ok, "wamid.t1-" + tag, "Ok merci, bien noté"));
        webhook("whatsapp.message.received", textMessage(ref, "wamid.t2-" + tag, "J'ai payé, voici la référence " + tx.getReference().toLowerCase()));
        webhook("whatsapp.message.received", textMessage(other, "wamid.t3-" + tag, "Qui est à l'appareil ?"));

        assertThat(caseOf(ok).getStatus()).isEqualTo(ReminderCaseStatus.ACCUSE);
        assertThat(caseOf(ref).getStatus()).isEqualTo(ReminderCaseStatus.VERIFICATION);
        ReminderCase toRead = caseOf(other);
        assertThat(toRead.isNeedsAttention()).isTrue();
        assertThat(responseRepository.findByReminderCaseIdOrderByReceivedAtAsc(toRead.getId()).get(0).getRawText())
            .isEqualTo("Qui est à l'appareil ?");

        JsonNode badge = json(mockMvc.perform(get("/api/v1/reminders/attention").with(as("SEND_REMINDERS")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(badge.get("count").asLong()).isGreaterThanOrEqualTo(1);
        mockMvc.perform(post("/api/v1/reminders/cases/" + toRead.getId() + "/acknowledge").with(as("SEND_REMINDERS")))
            .andExpect(status().isOk());
        assertThat(caseOf(other).isNeedsAttention()).isFalse();
    }

    @Test
    void messagesForTheTaskTrackerAreIgnored() throws Exception {
        Donor reminded = member("Wa Routage");
        Donor stranger = member("Wa Inconnu");
        send(reminded);
        Long caseId = caseOf(reminded).getId();

        // Bouton du suivi des tâches, puis texte d'un numéro à qui MLA Finance n'a pas écrit.
        webhook("whatsapp.message.received", button(reminded, "wamid.p1-" + tag, "FAIT|T42", "C'est fait"));
        webhook("whatsapp.message.received", textMessage(stranger, "wamid.p2-" + tag, "fait"));
        // Bouton MLA Finance envoyé depuis un autre numéro que celui du membre : ignoré.
        webhook("whatsapp.message.received", button(stranger, "wamid.p3-" + tag, "mlaf:done:" + caseId, "C'est déjà fait"));

        assertThat(responseRepository.findByReminderCaseIdOrderByReceivedAtAsc(caseId)).isEmpty();
        assertThat(caseOf(reminded).getStatus()).isEqualTo(ReminderCaseStatus.EN_ATTENTE_REPONSE);
        verify(kapso, never()).sendText(anyString(), anyString());
    }

    @Test
    void deliveryStatusesMoveForwardOnly() throws Exception {
        Donor donor = member("Wa Statut");
        send(donor);
        String wamid = deliveryRepository.findAll().stream()
            .filter(d -> d.getChannel() == org.mla.finance.domain.ReminderChannel.WHATSAPP && donor.getPhone().equals(d.getRecipient()))
            .findFirst().orElseThrow().getProviderMessageId();

        for (String st : List.of("delivered", "read", "delivered")) {
            webhook("whatsapp.message." + st, Map.of("message", Map.of("id", wamid,
                "kapso", Map.of("direction", "outbound", "status", st)), "phone_number_id", "123456"));
        }
        assertThat(deliveryRepository.findFirstByProviderMessageId(wamid).orElseThrow().getStatus())
            .isEqualTo(ReminderDeliveryStatus.READ);
    }

    // ------------------------------------------------------------------------------------------------ email

    @Test
    void emailResponsePageLetsTheMemberChooseADateOrDeclareThePayment() throws Exception {
        Donor donor = member("Wa Page");
        send(donor);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtml(eq(donor.getEmail()), anyString(), html.capture(), any());
        Matcher m = Pattern.compile("/relance/([A-Za-z0-9_-]+)\\?a=ack").matcher(html.getValue());
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        JsonNode view = json(mockMvc.perform(get("/api/v1/public/reminder-actions/" + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(view.get("monthLabel").asText()).isEqualTo("novembre 2026");
        assertThat(view.get("open").asBoolean()).isTrue();
        assertThat(view.toString()).doesNotContain(donor.getEmail(), donor.getPhone());

        JsonNode ack = json(mockMvc.perform(post("/api/v1/public/reminder-actions/" + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACK\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(ack.get("dateOptions")).isNotEmpty();

        mockMvc.perform(post("/api/v1/public/reminder-actions/" + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PROMISE\",\"date\":\"2026-11-27\"}"))
            .andExpect(status().isOk());
        assertThat(caseOf(donor).getStatus()).isEqualTo(ReminderCaseStatus.PROMIS);

        mockMvc.perform(get("/api/v1/public/reminder-actions/pas-un-vrai-jeton-xyz")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ texte libre

    @Test
    void freeTextClassification() {
        YearMonth nov = YearMonth.of(2026, 11);
        assertThat(ReminderResponseService.classify("C'est payé depuis hier", nov).type()).isEqualTo(ReminderResponseType.DONE);
        assertThat(ReminderResponseService.classify("Déjà fait !", nov).type()).isEqualTo(ReminderResponseType.DONE);
        assertThat(ReminderResponseService.classify("pas encore fait, désolé", nov).type()).isEqualTo(ReminderResponseType.ACK);
        assertThat(ReminderResponseService.classify("Ok bien reçu", nov).type()).isEqualTo(ReminderResponseType.ACK);
        ReminderResponseService.Classified date = ReminderResponseService.classify("je verse le 28/11", nov);
        assertThat(date.type()).isEqualTo(ReminderResponseType.PROMISE);
        assertThat(date.date()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(ReminderResponseService.classify("Je paie le 26", nov).date()).isEqualTo(LocalDate.of(2026, 11, 26));
        ReminderResponseService.Classified ref = ReminderResponseService.classify("ref mla-123456 merci", nov);
        assertThat(ref.type()).isEqualTo(ReminderResponseType.REFERENCE);
        assertThat(ref.reference()).isEqualTo("MLA-123456");
        assertThat(ReminderResponseService.classify("Bonjour, qui êtes-vous ?", nov).type()).isEqualTo(ReminderResponseType.FREE_TEXT);
    }
}
