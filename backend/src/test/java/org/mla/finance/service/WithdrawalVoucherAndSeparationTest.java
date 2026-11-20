package org.mla.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.PayoutStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PayoutOutcome;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bon de décaissement PDF (contenu, droits, refus hors PROCESSED, référence opérateur) et séparation
 * des rôles sur la validation et le rejet des retraits (refus 403, exception SUPER_ADMIN tracée).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.payment.pawapay.enabled=true",
    "app.payment.pawapay.api-token=test-token",
    "app.payment.pawapay.callback-secret=cb-secret-test",
    "app.payment.pawapay.poll-interval-ms=3600000",
    "app.payment.pawapay.poll-initial-delay-ms=3600000"
})
@Transactional
class WithdrawalVoucherAndSeparationTest {

    private static final List<String> VALIDATE = List.of("MANAGE_WITHDRAWALS", "VALIDATE_WITHDRAWALS");
    private static final String FULL_MSISDN = "237697272882";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired WithdrawalRepository withdrawalRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired WithdrawalPayoutService payoutService;
    @MockBean PawaPayClient client;

    Project project;
    User treasurer;
    User otherTreasurer;
    User superAdmin;

    @BeforeEach
    void setUp() {
        project = projectRepository.save(Project.builder().name("Sono de l'église").status(ProjectStatus.ACTIVE).build());
        treasurer = user("tresorier-a@test.local", "Awa Trésorière", "TRESORIER");
        otherTreasurer = user("tresorier-b@test.local", "Bruno Trésorier", "TRESORIER");
        superAdmin = user("admin-sep@test.local", "Sandra Admin", "SUPER_ADMIN");
        when(client.checkPayout(anyString())).thenReturn(PayoutOutcome.processing("PROCESSING"));
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

    private Withdrawal pending(User requester) {
        return withdrawalRepository.save(Withdrawal.builder()
            .project(project).amount(new BigDecimal("15000.00")).reason("Câbles micro")
            .status(WithdrawalStatus.PENDING).requestedBy(requester).requestedAt(Instant.now()).build());
    }

    private Withdrawal processedByMobileMoney(String providerRef) {
        return withdrawalRepository.save(Withdrawal.builder()
            .project(project).amount(new BigDecimal("25000.00")).reason("Achat de matériel <son & lumière>")
            .remark("Facture n° 42")
            .status(WithdrawalStatus.PROCESSED)
            .requestedBy(treasurer).requestedAt(Instant.parse("2026-11-18T08:00:00Z"))
            .decidedBy(otherTreasurer).decidedAt(Instant.parse("2026-11-18T09:30:00Z"))
            .processedAt(Instant.parse("2026-11-18T10:00:00Z")).operationDate(LocalDate.of(2026, 11, 18))
            .reference("RET-2026-0042")
            .payoutId("6f1d2c3b-0000-4000-8000-00000000abcd").payoutStatus(PayoutStatus.COMPLETED)
            .payoutPhone(FULL_MSISDN).payoutProvider("ORANGE_CMR")
            .payoutRequestedAt(Instant.parse("2026-11-18T09:59:00Z")).payoutCompletedAt(Instant.parse("2026-11-18T10:00:00Z"))
            .payoutProviderRef(providerRef)
            .build());
    }

    private long audits(Long id, String action) {
        return auditLogRepository.findAll().stream()
            .filter(a -> "WITHDRAWAL".equals(a.getEntityType()) && id.equals(a.getEntityId()) && action.equals(a.getAction()))
            .count();
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            return new PDFTextStripper().getText(doc);
        }
    }

    // ------------------------------------------------------------------ bon de décaissement

    @Test
    void mobileMoneyVoucherIsAReadableOnePagePdfWithoutFullNumber() throws Exception {
        Withdrawal w = processedByMobileMoney("MP261118.1000.A12345");

        byte[] pdf = mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf").with(as(superAdmin, List.of())))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                org.hamcrest.Matchers.containsString("bon-decaissement-RET-2026-0042.pdf")))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("Bon de décaissement", "RET-2026-0042", "25 000 FCFA", "Vingt-cinq mille francs CFA",
            "Sono de l'église", "Achat de matériel <son & lumière>", "Facture n° 42", "Awa Trésorière", "Bruno Trésorier",
            "Orange Money", "6** ** 28 82", "6f1d2c3b-0000-4000-8000-00000000abcd", "MP261118.1000.A12345",
            "Document généré par MLA Finance le");
        assertThat(text).contains("DÉCAISSEMENT MOBILE MONEY");
        assertThat(text).doesNotContain(FULL_MSISDN, "697272882", "SIGNATURES", "Auto-validé");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var name : doc.getPage(0).getResources().getFontNames()) {
                PDFont font = doc.getPage(0).getResources().getFont(name);
                assertThat(font.isEmbedded()).as("police %s embarquée", font.getName()).isTrue();
            }
        }
        assertThat(audits(w.getId(), "WITHDRAWAL_VOUCHER_DOWNLOADED")).isEqualTo(1);
        verify(client, never()).checkPayout(anyString());
    }

    @Test
    void missingOperatorReferenceIsReadFromWalletLedgerAndStored() throws Exception {
        Withdrawal w = processedByMobileMoney(null);
        when(client.checkPayout(w.getPayoutId())).thenReturn(PayoutOutcome.completed("MP-LATE-777"));

        byte[] pdf = mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf").with(as(treasurer, List.of())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(text(pdf)).contains("MP-LATE-777");
        assertThat(withdrawalRepository.findById(w.getId()).orElseThrow().getPayoutProviderRef()).isEqualTo("MP-LATE-777");
    }

    @Test
    void operatorReferenceUnavailableStillProducesTheVoucher() throws Exception {
        Withdrawal w = processedByMobileMoney(null);
        when(client.checkPayout(w.getPayoutId())).thenReturn(PayoutOutcome.unavailable("HTTP 503"));

        byte[] pdf = mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf").with(as(treasurer, List.of())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(text(pdf)).contains("Non communiquée par l'opérateur");
        assertThat(withdrawalRepository.findById(w.getId()).orElseThrow().getPayoutProviderRef()).isNull();
    }

    @Test
    void cashVoucherHasThreeSignatureBoxesAndSelfApprovalMention() throws Exception {
        Withdrawal w = withdrawalRepository.save(Withdrawal.builder()
            .project(project).amount(new BigDecimal("1250500")).reason("Location de salle")
            .status(WithdrawalStatus.PROCESSED).requestedBy(superAdmin).decidedBy(superAdmin)
            .requestedAt(Instant.now()).decidedAt(Instant.now()).processedAt(Instant.now()).operationDate(LocalDate.now())
            .build());

        byte[] pdf = mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf").with(as(treasurer, List.of())))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                org.hamcrest.Matchers.containsString("bon-decaissement-RET-" + w.getId() + ".pdf")))
            .andReturn().getResponse().getContentAsByteArray();

        String text = text(pdf);
        assertThat(text).contains("Un million deux cent cinquante mille cinq cents francs CFA", "SIGNATURES", "DEMANDEUR",
            "TRÉSORIER", "BÉNÉFICIAIRE", "Auto-validé par un super administrateur");
        assertThat(text).doesNotContain("DÉCAISSEMENT MOBILE MONEY");
    }

    @Test
    void voucherIsRefusedUntilTheWithdrawalIsProcessed() throws Exception {
        Withdrawal w = pending(treasurer);
        mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf").with(as(treasurer, List.of())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("décaissé")));
        assertThat(audits(w.getId(), "WITHDRAWAL_VOUCHER_DOWNLOADED")).isZero();
    }

    @Test
    void voucherRequiresAuthentication() throws Exception {
        Withdrawal w = processedByMobileMoney("MP1");
        mockMvc.perform(get("/api/v1/withdrawals/" + w.getId() + "/voucher.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void completedPayoutStoresTheOperatorReference() {
        Withdrawal w = withdrawalRepository.save(Withdrawal.builder()
            .project(project).amount(new BigDecimal("5000")).reason("Transport")
            .status(WithdrawalStatus.APPROVED).requestedBy(treasurer).decidedBy(otherTreasurer)
            .requestedAt(Instant.now()).decidedAt(Instant.now())
            .payoutId("11111111-2222-4333-8444-555555555555").payoutStatus(PayoutStatus.PROCESSING)
            .payoutPhone(FULL_MSISDN).payoutProvider("ORANGE_CMR").payoutRequestedAt(Instant.now())
            .build());
        when(client.checkPayout(w.getPayoutId())).thenReturn(PayoutOutcome.completed("MP-NOW-1"));

        payoutService.reconcile(w.getId());

        Withdrawal done = withdrawalRepository.findById(w.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(WithdrawalStatus.PROCESSED);
        assertThat(done.getPayoutProviderRef()).isEqualTo("MP-NOW-1");
    }

    // ------------------------------------------------------------------ séparation des rôles

    @Test
    void managerCannotApproveOrRejectWhatTheyEntered() throws Exception {
        Withdrawal w = pending(treasurer);

        mockMvc.perform(post("/api/v1/withdrawals/" + w.getId() + "/approve").with(as(treasurer, VALIDATE)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(
                "Vous ne pouvez pas valider un retrait que vous avez saisi : une autre personne doit le valider."));
        mockMvc.perform(post("/api/v1/withdrawals/" + w.getId() + "/reject").with(as(treasurer, VALIDATE))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("reason", "Doublon"))))
            .andExpect(status().isForbidden());

        assertThat(withdrawalRepository.findById(w.getId()).orElseThrow().getStatus()).isEqualTo(WithdrawalStatus.PENDING);
    }

    @Test
    void anotherPersonApprovesNormally() throws Exception {
        Withdrawal w = pending(treasurer);
        mockMvc.perform(post("/api/v1/withdrawals/" + w.getId() + "/approve").with(as(otherTreasurer, VALIDATE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.selfApproved").value(false))
            .andExpect(jsonPath("$.requestedById").value(treasurer.getId()));
        assertThat(audits(w.getId(), "WITHDRAWAL_SELF_APPROVED")).isZero();
    }

    @Test
    void superAdminMayApproveTheirOwnWithdrawalAndItIsTraced() throws Exception {
        Withdrawal own = pending(superAdmin);
        Withdrawal other = pending(treasurer);
        mockMvc.perform(post("/api/v1/withdrawals/" + other.getId() + "/approve").with(as(otherTreasurer, VALIDATE)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/withdrawals/" + own.getId() + "/approve").with(as(superAdmin, VALIDATE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selfApproved").value(true));
        assertThat(audits(own.getId(), "WITHDRAWAL_SELF_APPROVED")).isEqualTo(1);
        assertThat(audits(own.getId(), "APPROVE")).isEqualTo(1);

        String body = mockMvc.perform(get("/api/v1/withdrawals").param("selfApproved", "true").param("projectId",
                String.valueOf(project.getId())).with(as(treasurer, List.of())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id").asLong()).isEqualTo(own.getId());
    }

    @Test
    void superAdminMayRejectTheirOwnWithdrawal() throws Exception {
        Withdrawal own = pending(superAdmin);
        mockMvc.perform(post("/api/v1/withdrawals/" + own.getId() + "/reject").with(as(superAdmin, VALIDATE))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("reason", "Erreur de saisie"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.selfApproved").value(false));
    }
}
