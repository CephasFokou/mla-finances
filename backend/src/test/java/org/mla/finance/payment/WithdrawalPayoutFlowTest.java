package org.mla.finance.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.PayoutStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.payment.pawapay.DepositOutcome;
import org.mla.finance.payment.pawapay.MobileMoneyBalance;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PayoutOutcome;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.service.WithdrawalPayoutService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Décaissement d'un retrait APPROVED par Mobile Money (payout wallet-ledger), client HTTP simulé :
 * envoi et idempotence, mapping des réponses, statut final par webhook (corps jamais cru) et par
 * relecture, double callback, échec puis nouvelle tentative, blocages pendant un payout en cours,
 * permissions. Aucun appel réseau réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.payment.pawapay.enabled=true",
    "app.payment.pawapay.api-token=test-token",
    "app.payment.pawapay.callback-secret=cb-secret-test",
    "app.payment.pawapay.poll-interval-ms=3600000",
    "app.payment.pawapay.poll-initial-delay-ms=3600000",
    "app.rate-limit.webhook-requests-per-minute=10000"
})
@Transactional
class WithdrawalPayoutFlowTest {

    private static final List<String> MANAGE = List.of("MANAGE_WITHDRAWALS");
    private static final String ORANGE = "ORANGE_CMR-2882";
    private static final String MTN = "MTN_MOMO_CMR-9561";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired WithdrawalRepository withdrawalRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired WithdrawalPayoutService payoutService;
    @MockBean PawaPayClient client;

    Withdrawal withdrawal;

    private static RequestPostProcessor as(List<String> permissions) {
        return jwt()
            .jwt(b -> b.subject("999").claim("permissions", permissions))
            .authorities(permissions.stream().map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p)).toList());
    }

    @BeforeEach
    void setUp() {
        Project collecte = projectRepository.save(Project.builder().name("Collecte payout").status(ProjectStatus.ACTIVE).build());
        withdrawal = withdrawalRepository.save(Withdrawal.builder()
            .project(collecte)
            .amount(new BigDecimal("25000.00"))
            .reason("Achat de matériel")
            .status(WithdrawalStatus.APPROVED)
            .decidedAt(Instant.now())
            .build());
        when(client.initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(PayoutOutcome.processing("ACCEPTED"));
        when(client.checkPayout(anyString())).thenReturn(PayoutOutcome.processing("PROCESSING"));
    }

    private ResultActions payout(String recipientKey) throws Exception {
        return mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/payout").with(as(MANAGE))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("recipientKey", recipientKey))));
    }

    private JsonNode payoutOk(String recipientKey) throws Exception {
        String body = payout(recipientKey).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void webhook(Map<String, Object> body, String event) throws Exception {
        var request = post("/api/v1/webhooks/pawapay/cb-secret-test").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        if (event != null) {
            request.header("X-Ledger-Event", event);
        }
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private Withdrawal reload() {
        return withdrawalRepository.findById(withdrawal.getId()).orElseThrow();
    }

    private long audits(String action) {
        return auditLogRepository.findAll().stream()
            .filter(a -> "WITHDRAWAL".equals(a.getEntityType()) && withdrawal.getId().equals(a.getEntityId()) && action.equals(a.getAction()))
            .count();
    }

    @Test
    void optionsExposeTheClosedListWithMaskedNumbersOnly() throws Exception {
        mockMvc.perform(get("/api/v1/withdrawals/payout-options").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.recipients.length()").value(2))
            .andExpect(jsonPath("$.recipients[0].key").value(ORANGE))
            .andExpect(jsonPath("$.recipients[0].provider").value("ORANGE_CMR"))
            .andExpect(jsonPath("$.recipients[0].maskedPhone").value("6** ** 28 82"))
            .andExpect(jsonPath("$.recipients[1].key").value(MTN))
            .andExpect(jsonPath("$.recipients[0].msisdn").doesNotExist());
    }

    @Test
    void payoutIsPersistedBeforeTheCallAndSentWithTheWithdrawalAmountAndReference() throws Exception {
        JsonNode response = payoutOk(ORANGE);

        Withdrawal w = reload();
        assertThat(w.getPayoutId()).hasSize(36);
        assertThat(w.getPayoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(w.getPayoutPhone()).isEqualTo("237697272882");
        assertThat(w.getPayoutProvider()).isEqualTo("ORANGE_CMR");
        assertThat(w.getPayoutRequestedAt()).isNotNull();
        assertThat(w.getPayoutNextCheckAt()).isNotNull();
        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        verify(client).initiatePayout(eq(w.getPayoutId()), eq("25000"), eq("237697272882"), eq("ORANGE_CMR"),
            eq("MLA-WD-" + w.getId()), eq("MLA RETRAIT " + w.getId()));
        assertThat(audits("PAYOUT_REQUESTED")).isEqualTo(1);

        assertThat(response.get("payoutStatus").asText()).isEqualTo("PROCESSING");
        assertThat(response.get("payoutPhoneMasked").asText()).isEqualTo("6** ** 28 82");
        assertThat(response.toString()).doesNotContain("697272882");
    }

    @Test
    void clientReferenceUsesTheWithdrawalReferenceWhenThereIsOne() throws Exception {
        withdrawal.setReference("RET-2026-014");
        withdrawalRepository.save(withdrawal);
        payoutOk(MTN);
        verify(client).initiatePayout(anyString(), eq("25000"), eq("237651059561"), eq("MTN_MOMO_CMR"), eq("MLA-WD-RET-2026-014"), anyString());
    }

    @Test
    void onlyOneActivePayoutAndCancelOrManualProcessAreBlockedMeanwhile() throws Exception {
        payoutOk(ORANGE);

        payout(MTN).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/cancel").with(as(MANAGE))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/process").with(as(MANAGE))).andExpect(status().isConflict());

        verify(client, times(1)).initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
    }

    @Test
    void completedWebhookProcessesTheWithdrawalOnceEvenWhenReplayed() throws Exception {
        payoutOk(ORANGE);
        String payoutId = reload().getPayoutId();
        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.completed());

        webhook(Map.of("payoutId", payoutId, "status", "COMPLETED"), "payout");
        webhook(Map.of("payoutId", payoutId, "status", "COMPLETED"), "payout");

        Withdrawal w = reload();
        assertThat(w.getPayoutStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(w.getPayoutCompletedAt()).isNotNull();
        assertThat(w.getPayoutNextCheckAt()).isNull();
        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.PROCESSED);
        assertThat(w.getProcessedAt()).isNotNull();
        assertThat(w.getOperationDate()).isEqualTo(LocalDate.now());
        assertThat(w.getPaymentChannel().getCode()).isEqualTo("ORANGE_MONEY");
        assertThat(audits("PAYOUT_COMPLETED")).isEqualTo(1);
        // Le second webhook trouve un payout final : aucune relecture.
        verify(client, times(1)).checkPayout(payoutId);

        // Plus aucun changement possible une fois final.
        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.failed("FAILED", "OTHER_ERROR", null));
        payoutService.reconcileByPayoutId(payoutId);
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.PROCESSED);
    }

    @Test
    void mtnPayoutCompletedByPollingUsesTheMtnChannel() throws Exception {
        payoutOk(MTN);
        Withdrawal w = reload();
        when(client.checkPayout(w.getPayoutId())).thenReturn(PayoutOutcome.completed());

        payoutService.reconcile(w.getId());

        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.PROCESSED);
        assertThat(reload().getPaymentChannel().getCode()).isEqualTo("MTN_MOMO");
    }

    @Test
    void webhookBodyIsNeverTrusted() throws Exception {
        payoutOk(ORANGE);
        String payoutId = reload().getPayoutId();

        // Le corps dit COMPLETED, la relecture dit PROCESSING : rien ne change.
        webhook(Map.of("payoutId", payoutId, "status", "COMPLETED"), "payout");

        verify(client).checkPayout(payoutId);
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
    }

    @Test
    void webhookRoutesDepositsAndPayoutsSeparately() throws Exception {
        String depositId = "1f0e8c1a-4b7e-4a4a-9b1e-3c5d6e7f8a9b";
        when(client.checkDeposit(depositId)).thenReturn(DepositOutcome.pending("PROCESSING"));

        webhook(Map.of("depositId", depositId, "status", "COMPLETED"), "deposit");
        webhook(Map.of("depositId", depositId), null);
        // Incohérence en-tête / corps : ignoré.
        webhook(Map.of("depositId", depositId), "payout");
        // Identifiant invalide : ignoré, toujours 200.
        webhook(Map.of("payoutId", "pas-un-uuid"), "payout");

        // depositId inconnu côté MLA : aucune relecture, et jamais de relecture de payout.
        verify(client, never()).checkPayout(anyString());
        verify(client, never()).checkDeposit(anyString());

        payoutOk(ORANGE);
        String payoutId = reload().getPayoutId();
        webhook(Map.of("payoutId", payoutId), null);
        verify(client).checkPayout(payoutId);
        verify(client, never()).checkDeposit(anyString());
    }

    @Test
    void pendingApprovalIsShownThenCompletedOnceApprovedInTheTreasury() throws Exception {
        when(client.initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(PayoutOutcome.pendingApproval());
        JsonNode response = payoutOk(ORANGE);
        assertThat(response.get("payoutStatus").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(audits("PAYOUT_PENDING_APPROVAL")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/cancel").with(as(MANAGE))).andExpect(status().isConflict());

        String payoutId = reload().getPayoutId();
        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.pendingApproval());
        payoutService.reconcile(withdrawal.getId());
        assertThat(audits("PAYOUT_PENDING_APPROVAL")).isEqualTo(1);

        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.processing("ACCEPTED"));
        payoutService.reconcile(withdrawal.getId());
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.PROCESSING);

        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.completed());
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/payout/recheck").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"))
            .andExpect(jsonPath("$.payoutStatus").value("COMPLETED"));
    }

    @Test
    void rejectedPayoutFailsKeepsTheWithdrawalApprovedAndAllowsANewAttempt() throws Exception {
        when(client.initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(PayoutOutcome.failed("REJECTED", "INSUFFICIENT_PROJECT_BALANCE", "Solde insuffisant"));
        JsonNode first = payoutOk(ORANGE);
        assertThat(first.get("payoutStatus").asText()).isEqualTo("FAILED");
        assertThat(first.get("payoutFailureCode").asText()).isEqualTo("INSUFFICIENT_PROJECT_BALANCE");
        assertThat(first.get("status").asText()).isEqualTo("APPROVED");
        assertThat(reload().getPayoutNextCheckAt()).isNull();
        String firstPayoutId = reload().getPayoutId();
        assertThat(audits("PAYOUT_FAILED")).isEqualTo(1);

        when(client.initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(PayoutOutcome.processing("ACCEPTED"));
        JsonNode second = payoutOk(MTN);
        assertThat(second.get("payoutStatus").asText()).isEqualTo("PROCESSING");
        assertThat(second.get("payoutFailureCode").isNull()).isTrue();
        assertThat(reload().getPayoutId()).isNotEqualTo(firstPayoutId);

        // Un webhook tardif sur l'ancien payoutId ne touche pas au nouveau.
        webhook(Map.of("payoutId", firstPayoutId), "payout");
        verify(client, never()).checkPayout(firstPayoutId);
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
    }

    @Test
    void failedPayoutAfterAcceptanceKeepsTheWithdrawalApproved() throws Exception {
        payoutOk(ORANGE);
        String payoutId = reload().getPayoutId();
        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.failed("FAILED", "RECIPIENT_NOT_FOUND", null));

        payoutService.reconcileByPayoutId(payoutId);

        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(reload().getPayoutFailureCode()).isEqualTo("RECIPIENT_NOT_FOUND");
        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        // Le circuit manuel redevient possible.
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/process").with(as(MANAGE))).andExpect(status().isOk());
    }

    @Test
    void unknownOutcomeStaysProcessingAndNotFoundFailsOnlyAfterTheGracePeriod() throws Exception {
        when(client.initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(PayoutOutcome.unavailable("HTTP 503"));
        assertThat(payoutOk(ORANGE).get("payoutStatus").asText()).isEqualTo("PROCESSING");

        String payoutId = reload().getPayoutId();
        when(client.checkPayout(payoutId)).thenReturn(PayoutOutcome.notFound());
        payoutService.reconcile(withdrawal.getId());
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.PROCESSING);

        Withdrawal w = reload();
        w.setPayoutRequestedAt(Instant.now().minus(Duration.ofMinutes(10)));
        withdrawalRepository.save(w);
        payoutService.reconcile(withdrawal.getId());
        assertThat(reload().getPayoutStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(reload().getPayoutFailureCode()).isEqualTo("PAYOUT_NOT_FOUND");
        assertThat(reload().getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
    }

    @Test
    void onlyApprovedWithdrawalsWithAnApprovedRecipientCanBePaidOut() throws Exception {
        payout("ORANGE_CMR-0000").andExpect(status().isUnprocessableEntity());
        verify(client, never()).initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        withdrawal.setStatus(WithdrawalStatus.PENDING);
        withdrawalRepository.save(withdrawal);
        payout(ORANGE).andExpect(status().isConflict());
        assertThat(reload().getPayoutId()).isNull();
    }

    @Test
    void payoutActionsRequireManageWithdrawals() throws Exception {
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/payout").with(as(List.of("VALIDATE_WITHDRAWALS")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientKey\":\"" + ORANGE + "\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/payout/recheck").with(as(List.of("VALIDATE_WITHDRAWALS"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/withdrawals/" + withdrawal.getId() + "/payout")
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientKey\":\"" + ORANGE + "\"}"))
            .andExpect(status().isUnauthorized());
        verify(client, never()).initiatePayout(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void balanceIsReadFromWalletLedger() throws Exception {
        when(client.balance()).thenReturn(Optional.of(
            new MobileMoneyBalance("XAF", new BigDecimal("120000"), new BigDecimal("25000"), Instant.parse("2026-11-20T10:00:00Z"))));
        mockMvc.perform(get("/api/v1/withdrawals/mobile-money-balance").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.reachable").value(true))
            .andExpect(jsonPath("$.available").value(120000))
            .andExpect(jsonPath("$.reserved").value(25000));

        when(client.balance()).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/withdrawals/mobile-money-balance").with(as(MANAGE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reachable").value(false));
    }
}
