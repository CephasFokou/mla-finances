package org.mla.finance.payment.fees;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.MobileMoneyFee;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.PayoutStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.payment.pawapay.LedgerMovement;
import org.mla.finance.payment.pawapay.MobileMoneyBalance;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PawaPayProvider;
import org.mla.finance.repository.AuditLogRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.MobileMoneyFeeRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frais Mobile Money lus dans le grand livre wallet-ledger (client simulé) : imputation à la bonne
 * enveloppe, opération introuvable (Caisse MLA), une écriture par jour et par enveloppe, idempotence,
 * reprise de tout l'historique au premier passage, grand livre incomplet, contrôle d'écart.
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
    "app.payment.fees.sync-cron=0 0 0 1 1 *"
})
@Transactional
class MobileMoneyFeeSyncTest {

    private static final Instant DAY1 = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-11-19T10:00:00Z");

    @Autowired MobileMoneyFeeSyncService syncService;
    @Autowired MockMvc mockMvc;
    @Autowired ProjectRepository projectRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired PaymentChannelRepository channelRepository;
    @Autowired PaymentTransactionRepository transactionRepository;
    @Autowired WithdrawalRepository withdrawalRepository;
    @Autowired MobileMoneyFeeRepository feeRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockBean PawaPayClient client;

    Project collecte;
    DonationCategory sono;
    DonationCategory caisse;
    PaymentChannel orange;
    Withdrawal payoutWithdrawal;
    long nextId = 1000;

    @BeforeEach
    void setUp() {
        collecte = projectRepository.save(Project.builder().name("Collecte instruments").status(ProjectStatus.ACTIVE).build());
        sono = categoryRepository.save(DonationCategory.builder().name("Fonds sono").build());
        caisse = categoryRepository.findFirstByMainFundTrue().orElseThrow();
        orange = channelRepository.findByCode("ORANGE_MONEY").orElseThrow();
        transactionRepository.save(PaymentTransaction.builder()
            .project(collecte).paymentChannel(orange).amount(new BigDecimal("5000")).provider(PawaPayProvider.CODE)
            .status(PaymentTransactionStatus.CONFIRMED).reference("DON-TEST-0001").depositId("d0000000-0000-4000-8000-000000000001")
            .confirmedAt(DAY1).build());
        payoutWithdrawal = withdrawalRepository.save(Withdrawal.builder()
            .category(sono).amount(new BigDecimal("20000")).reason("Câbles").status(WithdrawalStatus.PROCESSED)
            .reference("RET-SONO-7").paymentChannel(orange)
            .payoutId("a0000000-0000-4000-8000-000000000002").payoutStatus(PayoutStatus.COMPLETED).payoutCompletedAt(DAY1)
            .requestedAt(DAY1).processedAt(DAY1).operationDate(LocalDate.of(2026, 11, 18)).build());
    }

    private LedgerMovement fee(String amount, String depositId, String payoutId, String clientRef, Instant at) {
        return new LedgerMovement(nextId++, "FEE", "COMPLETED", new BigDecimal(amount), depositId, payoutId, null, clientRef,
            "Deposit PawaPay fees", at);
    }

    private void ledger(List<LedgerMovement> movements) {
        when(client.movements(any(), anyInt(), anyInt())).thenReturn(Optional.of(new LedgerMovement.Page(movements, 0, 1)));
    }

    private List<LedgerMovement> sample() {
        List<LedgerMovement> m = new ArrayList<>();
        m.add(fee("-150", "d0000000-0000-4000-8000-000000000001", null, "DON-TEST-0001", DAY1));          // don -> collecte
        m.add(fee("-200", null, "a0000000-0000-4000-8000-000000000002", "MLA-WD-RET-SONO-7", DAY1));      // payout -> sono
        m.add(fee("-75", null, "b0000000-0000-4000-8000-0000000000ff", "MLA-WD-RET-SONO-7", DAY1));       // ancienne tentative -> sono
        m.add(fee("-50", null, null, null, DAY1));                                                        // frais global -> caisse
        m.add(fee("-30", "c0000000-0000-4000-8000-0000000000ee", null, null, DAY1));                      // don inconnu -> caisse
        m.add(fee("-40", "d0000000-0000-4000-8000-000000000001", null, "DON-TEST-0001", DAY2));          // autre jour -> collecte
        m.add(new LedgerMovement(nextId++, "DEPOSIT", "COMPLETED", new BigDecimal("5000"), "d0000000-0000-4000-8000-000000000001",
            null, null, "DON-TEST-0001", "Deposit", DAY1));                                              // pas un frais
        m.add(new LedgerMovement(nextId++, "FEE", "PENDING", new BigDecimal("-10"), null, null, null, null, "en vol", DAY1)); // pas final
        return m;
    }

    private List<Withdrawal> feeEntries() {
        return withdrawalRepository.findAll().stream().filter(w -> w.getNature() == MovementNature.FEE).toList();
    }

    private BigDecimal entry(Long projectId, Long categoryId, LocalDate day) {
        return feeEntries().stream()
            .filter(w -> (projectId != null && w.getProject() != null && projectId.equals(w.getProject().getId()))
                || (categoryId != null && w.getCategory() != null && categoryId.equals(w.getCategory().getId())))
            .filter(w -> day.equals(w.getOperationDate()))
            .map(Withdrawal::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void feesAreBookedOnTheEnvelopeOfTheirOperationOncePerDayAndEnvelope() {
        ledger(sample());

        MobileMoneyFeeSyncService.Result result = syncService.sync();

        assertThat(result.complete()).isTrue();
        assertThat(result.feesBooked()).isEqualTo(6);
        assertThat(result.entries()).isEqualTo(4);
        assertThat(result.total()).isEqualByComparingTo("545");
        LocalDate d1 = LocalDate.of(2026, 11, 18);
        assertThat(entry(collecte.getId(), null, d1)).isEqualByComparingTo("150");
        assertThat(entry(collecte.getId(), null, LocalDate.of(2026, 11, 19))).isEqualByComparingTo("40");
        assertThat(entry(null, sono.getId(), d1)).isEqualByComparingTo("275");
        assertThat(entry(null, caisse.getId(), d1)).isEqualByComparingTo("80");

        Withdrawal sonoEntry = feeEntries().stream().filter(w -> w.getCategory() != null && w.getCategory().getId().equals(sono.getId()))
            .findFirst().orElseThrow();
        assertThat(sonoEntry.getStatus()).isEqualTo(WithdrawalStatus.PROCESSED);
        assertThat(sonoEntry.getReason()).isEqualTo("Frais Mobile Money du 18/11/2026");
        assertThat(sonoEntry.getRemark()).contains("retrait RET-SONO-7 200 FCFA", "retrait RET-SONO-7 75 FCFA");
        assertThat(sonoEntry.getRequestedBy().getFullName()).isEqualTo("Trésorerie PawaPay");
        assertThat(sonoEntry.getDecidedBy()).isNull();
        assertThat(sonoEntry.isSelfApproved()).isFalse();
        assertThat(sonoEntry.getPaymentChannel().getCode()).isEqualTo("ORANGE_MONEY");

        assertThat(feeRepository.findAll()).extracting(MobileMoneyFee::getAttribution)
            .containsExactlyInAnyOrder(MobileMoneyFee.Attribution.OPERATION, MobileMoneyFee.Attribution.OPERATION,
                MobileMoneyFee.Attribution.CLIENT_REFERENCE, MobileMoneyFee.Attribution.MAIN_FUND, MobileMoneyFee.Attribution.MAIN_FUND,
                MobileMoneyFee.Attribution.OPERATION);
        assertThat(auditLogRepository.findAll().stream().filter(a -> "MOBILE_MONEY_FEES".equals(a.getAction())).count()).isEqualTo(4);
    }

    @Test
    void runningTwiceCreatesNoDuplicate() {
        ledger(sample());
        syncService.sync();
        int entries = feeEntries().size();

        MobileMoneyFeeSyncService.Result second = syncService.sync();

        assertThat(second.complete()).isTrue();
        assertThat(second.feesBooked()).isZero();
        assertThat(feeEntries()).hasSize(entries);
        assertThat(feeRepository.count()).isEqualTo(6);
    }

    @Test
    void firstPassReadsTheWholeHistoryThenOnlyARecentWindow() {
        ledger(sample());
        syncService.sync();
        verify(client).movements(isNull(), eq(0), anyInt());

        syncService.sync();
        verify(client).movements(eq(LocalDate.now(MobileMoneyFeeSyncService.DOUALA).minusDays(31)), eq(0), anyInt());
    }

    @Test
    void historyOnSeveralPagesIsReadEntirely() {
        when(client.movements(isNull(), eq(0), anyInt()))
            .thenReturn(Optional.of(new LedgerMovement.Page(List.of(fee("-150", "d0000000-0000-4000-8000-000000000001", null, null, DAY2)), 0, 2)));
        when(client.movements(isNull(), eq(1), anyInt()))
            .thenReturn(Optional.of(new LedgerMovement.Page(List.of(fee("-50", null, null, null, DAY1)), 1, 2)));

        assertThat(syncService.sync().feesBooked()).isEqualTo(2);
    }

    @Test
    void incompleteLedgerBooksNothing() {
        when(client.movements(isNull(), eq(0), anyInt()))
            .thenReturn(Optional.of(new LedgerMovement.Page(List.of(fee("-150", null, null, null, DAY1)), 0, 2)));
        when(client.movements(isNull(), eq(1), anyInt())).thenReturn(Optional.empty());

        MobileMoneyFeeSyncService.Result result = syncService.sync();

        assertThat(result.complete()).isFalse();
        assertThat(feeEntries()).isEmpty();
        assertThat(feeRepository.count()).isZero();
    }

    @Test
    void lateFeeForAnAlreadyBookedDayGetsAComplementaryEntry() {
        ledger(new ArrayList<>(List.of(fee("-150", "d0000000-0000-4000-8000-000000000001", null, null, DAY1))));
        syncService.sync();
        ledger(List.of(fee("-25", "d0000000-0000-4000-8000-000000000001", null, null, DAY1)));

        syncService.sync();

        assertThat(entry(collecte.getId(), null, LocalDate.of(2026, 11, 18))).isEqualByComparingTo("175");
        assertThat(feeEntries()).hasSize(2);
    }

    @Test
    void balanceCardShowsTheGapWithWalletLedger() throws Exception {
        ledger(sample());
        syncService.sync();
        // Ouverture 0 au 17/11 ; attendu = 5 000 (don) − 20 000 (payout) − 545 (frais) = −15 545.
        when(client.balance()).thenReturn(Optional.of(new MobileMoneyBalance("XAF", new BigDecimal("-15600"), BigDecimal.ZERO,
            Instant.now(), BigDecimal.ZERO, Instant.parse("2026-11-17T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/withdrawals/mobile-money-balance").with(jwt()
                .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority("MANAGE_WITHDRAWALS")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expected").value(-15545))
            .andExpect(jsonPath("$.gap").value(-55))
            .andExpect(jsonPath("$.checkSince").value("2026-11-17T00:00:00Z"));
    }

    @Test
    void gapIsNotComputedWithoutACutoverDate() throws Exception {
        when(client.balance()).thenReturn(Optional.of(new MobileMoneyBalance("XAF", new BigDecimal("1000"), BigDecimal.ZERO, Instant.now())));

        mockMvc.perform(get("/api/v1/withdrawals/mobile-money-balance").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(1000))
            .andExpect(jsonPath("$.gap").doesNotExist());
    }
}
