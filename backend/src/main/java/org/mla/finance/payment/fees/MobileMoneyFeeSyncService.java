package org.mla.finance.payment.fees;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.MobileMoneyFee;
import org.mla.finance.domain.MobileMoneyFee.Attribution;
import org.mla.finance.domain.MobileMoneyFee.OperationKind;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.User;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.payment.pawapay.LedgerMovement;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.MobileMoneyFeeRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Frais Mobile Money (PawaPay) enregistrés automatiquement dans MLA Finance, pour que la caisse ne
 * s'écarte plus du solde Mobile Money réel. Source : les lignes FEE COMPLETED du grand livre du projet
 * MLA chez wallet-ledger (GET /api/v1/movements), que wallet-ledger rattache à l'opération qui les a
 * causées grâce à l'import quotidien des relevés PawaPay.
 * <ul>
 *   <li>Imputation : enveloppe de la contribution pour un don (depositId), enveloppe du retrait pour un
 *       payout (payoutId), à défaut par la clientReferenceId (MLA-WD-… pour un retrait, référence du don
 *       sinon), et en dernier recours la caisse principale (Caisse MLA).</li>
 *   <li>Forme : une dépense directement PROCESSED, nature « Frais », saisie par l'utilisateur système
 *       « Trésorerie PawaPay », une par jour (date du frais, heure de Yaoundé) et par enveloppe, avec le
 *       détail des opérations dans la remarque. Seule exception au circuit de validation : ces frais ont
 *       déjà été prélevés par l'opérateur, il n'y a rien à décider.</li>
 *   <li>Idempotence : chaque mouvement compté est mémorisé (mobile_money_fees, identifiant unique) ;
 *       relancer ne crée aucun doublon. Un frais arrivé en retard sur un jour déjà compté produit une
 *       écriture complémentaire pour ce jour.</li>
 *   <li>Reprise : au premier passage (aucun frais encore compté), tout l'historique du projet MLA ;
 *       ensuite, une fenêtre glissante (lookback-days). Si une page manque, rien n'est compté.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MobileMoneyFeeSyncService {

    private static final Logger log = LoggerFactory.getLogger(MobileMoneyFeeSyncService.class);
    public static final String SYSTEM_USER_EMAIL = "tresorerie-pawapay@systeme.mla-finance";
    static final ZoneId DOUALA = ZoneId.of("Africa/Douala");
    static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 500;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String WITHDRAWAL_REF_PREFIX = "MLA-WD-";

    private final PawaPayClient client;
    private final MobileMoneyFeesProperties properties;
    private final MobileMoneyFeeRepository feeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final DonationCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;

    /** Bilan d'un passage. complete=false : grand livre incomplet, rien n'a été compté. */
    public record Result(boolean complete, int feesRead, int feesBooked, int entries, BigDecimal total) {
        static Result incomplete() {
            return new Result(false, 0, 0, 0, BigDecimal.ZERO);
        }
    }

    public Result sync() {
        boolean firstPass = feeRepository.count() == 0;
        LocalDate from = firstPass ? null : LocalDate.now(DOUALA).minusDays(properties.getLookbackDays());
        Optional<List<LedgerMovement>> read = readFees(from);
        if (read.isEmpty()) {
            log.warn("Frais Mobile Money : grand livre wallet-ledger incomplet, aucun frais compté (nouvel essai au prochain passage)");
            return Result.incomplete();
        }
        List<LedgerMovement> fees = read.get();
        Set<Long> known = new HashSet<>();
        List<Long> ids = fees.stream().map(LedgerMovement::id).filter(Objects::nonNull).toList();
        for (int i = 0; i < ids.size(); i += 500) {
            known.addAll(feeRepository.findKnownMovementIds(ids.subList(i, Math.min(ids.size(), i + 500))));
        }
        List<LedgerMovement> fresh = new ArrayList<>();
        for (LedgerMovement m : fees) {
            if (m.id() == null || m.occurredAt() == null || m.amount() == null || known.contains(m.id())) {
                continue;
            }
            if (m.amount().signum() >= 0) {
                log.warn("Frais Mobile Money : mouvement {} de montant {} ignoré (un frais est une sortie)", m.id(), m.amount());
                continue;
            }
            fresh.add(m);
        }
        if (fresh.isEmpty()) {
            log.info("Frais Mobile Money : {} frais lus{}, aucun nouveau", fees.size(), firstPass ? " (reprise)" : "");
            return new Result(true, fees.size(), 0, 0, BigDecimal.ZERO);
        }
        Result result = new TransactionTemplate(transactionManager).execute(s -> book(fresh, fees.size()));
        log.info("Frais Mobile Money{} : {} nouveaux frais, {} FCFA, {} écriture(s)", firstPass ? " (reprise)" : "",
            result.feesBooked(), result.total().stripTrailingZeros().toPlainString(), result.entries());
        return result;
    }

    /** Toutes les pages ; vide si l'une d'elles manque (rien de partiel). */
    private Optional<List<LedgerMovement>> readFees(LocalDate from) {
        List<LedgerMovement> fees = new ArrayList<>();
        int totalPages = 1;
        for (int page = 0; page < totalPages && page < MAX_PAGES; page++) {
            Optional<LedgerMovement.Page> result = client.movements(from, page, PAGE_SIZE);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            totalPages = result.get().totalPages();
            result.get().content().stream().filter(LedgerMovement::isCompletedFee).forEach(fees::add);
        }
        if (totalPages > MAX_PAGES) {
            log.error("Frais Mobile Money : grand livre de plus de {} pages, lecture interrompue", MAX_PAGES);
            return Optional.empty();
        }
        return Optional.of(fees);
    }

    // ------------------------------------------------------------------ écriture

    private record Target(Project project, DonationCategory category, PaymentChannel channel, String label,
                          OperationKind kind, String operationId, Attribution attribution) {

        String envelopeKey() {
            return project != null ? "P" + project.getId() : "C" + category.getId();
        }

        String envelopeName() {
            return project != null ? project.getName() : category.getName();
        }
    }

    private record Line(LedgerMovement movement, Target target) {
        BigDecimal fee() {
            return movement.amount().negate();
        }
    }

    private Result book(List<LedgerMovement> fresh, int read) {
        DonationCategory mainFund = categoryRepository.findFirstByMainFundTrue()
            .orElseThrow(() -> new IllegalStateException("Caisse MLA (catégorie principale) introuvable"));
        User system = userRepository.findByEmail(SYSTEM_USER_EMAIL).orElse(null);

        Map<String, List<Line>> groups = new LinkedHashMap<>();
        for (LedgerMovement m : fresh) {
            Target target = resolve(m, mainFund);
            LocalDate day = m.occurredAt().atZone(DOUALA).toLocalDate();
            groups.computeIfAbsent(target.envelopeKey() + "|" + day, k -> new ArrayList<>()).add(new Line(m, target));
        }

        Instant now = Instant.now();
        BigDecimal total = BigDecimal.ZERO;
        int entries = 0;
        for (List<Line> lines : groups.values()) {
            Target first = lines.get(0).target();
            LocalDate day = lines.get(0).movement().occurredAt().atZone(DOUALA).toLocalDate();
            BigDecimal amount = lines.stream().map(Line::fee).reduce(BigDecimal.ZERO, BigDecimal::add);
            Withdrawal entry = withdrawalRepository.save(Withdrawal.builder()
                .project(first.project())
                .category(first.category())
                .amount(amount)
                .nature(MovementNature.FEE)
                .reason("Frais Mobile Money du " + DAY.format(day))
                .remark(remark(lines))
                .paymentChannel(uniformChannel(lines))
                .status(WithdrawalStatus.PROCESSED)
                .requestedBy(system)
                .requestedAt(now)
                .decidedAt(now)
                .processedAt(now)
                .operationDate(day)
                .build());
            for (Line line : lines) {
                LedgerMovement m = line.movement();
                feeRepository.save(MobileMoneyFee.builder()
                    .ledgerMovementId(m.id())
                    .amount(line.fee())
                    .occurredAt(m.occurredAt())
                    .operationKind(line.target().kind())
                    .operationId(line.target().operationId())
                    .clientReference(truncate(m.clientReferenceId(), 100))
                    .attribution(line.target().attribution())
                    .withdrawal(entry)
                    .syncedAt(now)
                    .build());
            }
            auditLogService.record("WITHDRAWAL", entry.getId(), "MOBILE_MONEY_FEES",
                "Frais Mobile Money du " + DAY.format(day) + " : " + plain(amount) + " FCFA (" + lines.size()
                    + " frais) sur " + first.envelopeName()
                    + ", enregistrés automatiquement depuis la Trésorerie");
            total = total.add(amount);
            entries++;
        }
        feeRepository.flush();
        return new Result(true, read, fresh.size(), entries, total);
    }

    private Target resolve(LedgerMovement m, DonationCategory mainFund) {
        if (m.depositId() != null) {
            Optional<PaymentTransaction> tx = paymentTransactionRepository.findByDepositId(m.depositId());
            if (tx.isPresent()) {
                return fromDeposit(tx.get(), OperationKind.DEPOSIT, m.depositId(), Attribution.OPERATION, mainFund);
            }
        }
        if (m.payoutId() != null) {
            Optional<Withdrawal> w = withdrawalRepository.findByPayoutId(m.payoutId());
            if (w.isPresent()) {
                return fromWithdrawal(w.get(), m.payoutId(), Attribution.OPERATION, mainFund);
            }
        }
        String ref = m.clientReferenceId();
        if (ref != null && !ref.isBlank()) {
            if (ref.startsWith(WITHDRAWAL_REF_PREFIX)) {
                // Payout d'une tentative précédente (le retrait ne garde que le dernier payoutId).
                Optional<Withdrawal> w = withdrawalByClientReference(ref.substring(WITHDRAWAL_REF_PREFIX.length()));
                if (w.isPresent()) {
                    return fromWithdrawal(w.get(), m.payoutId(), Attribution.CLIENT_REFERENCE, mainFund);
                }
            } else {
                Optional<PaymentTransaction> tx = paymentTransactionRepository.findByReference(ref);
                if (tx.isPresent()) {
                    return fromDeposit(tx.get(), OperationKind.DEPOSIT, m.depositId(), Attribution.CLIENT_REFERENCE, mainFund);
                }
            }
        }
        OperationKind kind = m.payoutId() != null ? OperationKind.PAYOUT : m.depositId() != null ? OperationKind.DEPOSIT : OperationKind.NONE;
        String operationId = m.payoutId() != null ? m.payoutId() : m.depositId();
        String label = operationId != null ? "opération " + operationId + " introuvable" : "frais global réparti par la Trésorerie";
        return new Target(null, mainFund, null, label, kind, operationId, Attribution.MAIN_FUND);
    }

    private Optional<Withdrawal> withdrawalByClientReference(String ref) {
        Optional<Withdrawal> byReference = withdrawalRepository.findFirstByReference(ref);
        if (byReference.isPresent()) {
            return byReference;
        }
        try {
            return withdrawalRepository.findById(Long.valueOf(ref));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Target fromDeposit(PaymentTransaction tx, OperationKind kind, String depositId, Attribution attribution,
        DonationCategory mainFund) {
        Contribution c = tx.getContribution();
        Project project = c != null ? c.getProject() : tx.getProject();
        DonationCategory category = c != null ? c.getCategory() : tx.getCategory();
        String label = "don " + (tx.getReference() != null ? tx.getReference() : "#" + tx.getId());
        if (project == null && category == null) {
            return new Target(null, mainFund, tx.getPaymentChannel(), label, kind, depositId, Attribution.MAIN_FUND);
        }
        return new Target(project, project == null ? category : null, tx.getPaymentChannel(), label, kind, depositId, attribution);
    }

    private static Target fromWithdrawal(Withdrawal w, String payoutId, Attribution attribution, DonationCategory mainFund) {
        String label = "retrait " + (w.getReference() != null ? w.getReference() : "#" + w.getId());
        if (w.getProject() == null && w.getCategory() == null) {
            return new Target(null, mainFund, w.getPaymentChannel(), label, OperationKind.PAYOUT, payoutId, Attribution.MAIN_FUND);
        }
        return new Target(w.getProject(), w.getProject() == null ? w.getCategory() : null, w.getPaymentChannel(), label,
            OperationKind.PAYOUT, payoutId, attribution);
    }

    private static PaymentChannel uniformChannel(List<Line> lines) {
        PaymentChannel channel = lines.get(0).target().channel();
        for (Line line : lines) {
            PaymentChannel c = line.target().channel();
            if (channel == null || c == null || !Objects.equals(channel.getId(), c.getId())) {
                return null;
            }
        }
        return channel;
    }

    /** « Frais PawaPay : don DON-…-0012 150 FCFA ; retrait #12 200 FCFA » (500 caractères au plus). */
    private static String remark(List<Line> lines) {
        StringBuilder b = new StringBuilder("Frais PawaPay (Trésorerie) : ");
        for (int i = 0; i < lines.size(); i++) {
            String part = (i > 0 ? " ; " : "") + lines.get(i).target().label() + " " + plain(lines.get(i).fee()) + " FCFA";
            if (b.length() + part.length() > 480) {
                b.append(" ; … et ").append(lines.size() - i).append(" autre(s)");
                break;
            }
            b.append(part);
        }
        return b.toString();
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
