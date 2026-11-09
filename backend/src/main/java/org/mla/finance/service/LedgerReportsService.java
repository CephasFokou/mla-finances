package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.repository.ActivityRepository;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.ActivityResponse;
import org.mla.finance.web.dto.CategoryRef;
import org.mla.finance.web.dto.LedgerReports;
import org.mla.finance.web.dto.ProjectRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rapports « métier » du MLA, calculés en mémoire à partir de toutes les écritures : quelques
 * centaines de lignes par an, donc un regroupement en Java reste instantané et évite toute fonction
 * SQL propre à un moteur (H2 en dev, PostgreSQL en local/prod). Règles reprises du fichier de suivi
 * (claude/suivi-financier-2026-regles-de-gestion-confirmees.md) :
 * <ul>
 *   <li>la caisse principale (Caisse MLA) et les collectes dédiées ne se mélangent jamais ;</li>
 *   <li>les contributions mensuelles se comptent par mois d'attribution, pas par date de dépôt ;</li>
 *   <li>« responsables » = membres de rôle RESPONSABLE, « membres » = toutes les autres personnes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerReportsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ContributionRepository contributionRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final DonationCategoryRepository categoryRepository;
    private final ProjectRepository projectRepository;
    private final DonorRepository donorRepository;
    private final ActivityRepository activityRepository;

    public LedgerReports.Summary summary(int year) {
        List<Contribution> contributions = contributionRepository.findAllForReports();
        List<Withdrawal> processed = processed(withdrawalRepository.findAllForReports());
        DonationCategory mainFund = categoryRepository.findFirstByMainFundTrue().orElse(null);

        BigDecimal mainFundBalance = mainFund == null ? BigDecimal.ZERO
            : fundBalance(contributions, processed, mainFund.getId(), LocalDate.MAX);

        List<LedgerReports.CollectionRow> collections = collections(contributions, processed);
        List<LedgerReports.CollectionRow> open = collections.stream()
            .filter(c -> c.status() == ProjectStatus.ACTIVE).toList();
        BigDecimal openBalance = sum(open.stream().map(LedgerReports.CollectionRow::remaining));

        List<Contribution> monthly = monthlyContributions(contributions, year);
        List<LedgerReports.MonthSummary> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth month = YearMonth.of(year, m);
            List<Contribution> ofMonth = monthly.stream().filter(c -> YearMonth.from(c.getPeriodMonth()).equals(month)).toList();
            Totals t = totals(ofMonth);
            BigDecimal endOfMonth = mainFund == null ? null
                : fundBalance(contributions, processed, mainFund.getId(), month.atEndOfMonth());
            months.add(new LedgerReports.MonthSummary(month.toString(), t.respContributors, t.respAmount,
                t.memberContributors, t.memberAmount, t.unattributed, t.total(), t.share(), endOfMonth));
        }
        Totals y = totals(monthly);
        LedgerReports.YearSummary yearTotals = new LedgerReports.YearSummary(y.respContributors, y.respAmount,
            y.memberContributors, y.memberAmount, y.unattributed, y.total(), y.share());

        return new LedgerReports.Summary(
            year,
            CategoryRef.from(mainFund),
            mainFundBalance,
            openBalance,
            open.size(),
            y.total(),
            donorRepository.countByMemberRoleAndMemberStatus(MemberRole.RESPONSABLE, MemberStatus.ACTIVE),
            donorRepository.countByMemberRoleAndMemberStatus(MemberRole.MEMBRE, MemberStatus.ACTIVE),
            months,
            yearTotals,
            Instant.now());
    }

    public LedgerReports.Calendar calendar(int year) {
        List<Contribution> monthly = monthlyContributions(contributionRepository.findAllForReports(), year);
        Map<Long, BigDecimal[]> byDonor = new HashMap<>();
        BigDecimal[] unattributed = zeros();
        for (Contribution c : monthly) {
            int index = c.getPeriodMonth().getMonthValue() - 1;
            BigDecimal[] target = c.getDonor() != null && c.getDonor().isMember()
                ? byDonor.computeIfAbsent(c.getDonor().getId(), id -> zeros())
                : unattributed;
            target[index] = target[index].add(c.getAmount());
        }
        List<LedgerReports.CalendarRow> rows = donorRepository.findAll().stream()
            .filter(Donor::isMember)
            .sorted(Comparator
                .comparing((Donor d) -> d.getMemberRole().ordinal())
                .thenComparing(d -> d.getMemberStatus() == null ? 0 : d.getMemberStatus().ordinal())
                .thenComparing(d -> d.getLabel().toLowerCase()))
            .map(d -> {
                BigDecimal[] amounts = byDonor.getOrDefault(d.getId(), zeros());
                int monthsCount = (int) Stream.of(amounts).filter(a -> a.signum() > 0).count();
                return new LedgerReports.CalendarRow(d.getId(), d.getLabel(), d.getMemberRole(), d.getMemberStatus(),
                    List.of(amounts), sum(Stream.of(amounts)), monthsCount);
            })
            .toList();
        BigDecimal[] totals = zeros();
        for (int i = 0; i < 12; i++) {
            final int month = i;
            totals[i] = sum(rows.stream().map(r -> r.amounts().get(month))).add(unattributed[i]);
        }
        return new LedgerReports.Calendar(year, rows, List.of(unattributed), List.of(totals));
    }

    public List<LedgerReports.CollectionRow> collections() {
        return collections(contributionRepository.findAllForReports(), processed(withdrawalRepository.findAllForReports()));
    }

    public List<LedgerReports.ActivityRow> activities() {
        List<Contribution> contributions = contributionRepository.findAllForReports();
        List<Withdrawal> processed = processed(withdrawalRepository.findAllForReports());
        return activityRepository.findAll().stream()
            .sorted(Comparator.comparing(Activity::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Activity::getName))
            .map(a -> {
                Predicate<Activity> same = other -> other != null && other.getId().equals(a.getId());
                List<Contribution> in = contributions.stream().filter(c -> same.test(c.getActivity())).toList();
                List<Withdrawal> out = processed.stream().filter(w -> same.test(w.getActivity())).toList();
                BigDecimal expenses = sum(out.stream().map(Withdrawal::getAmount));
                BigDecimal returns = sum(in.stream().map(Contribution::getAmount));
                return new LedgerReports.ActivityRow(ActivityResponse.from(a), expenses, returns,
                    expenses.subtract(returns), in.size() + out.size());
            })
            .toList();
    }

    // --- calculs partagés -------------------------------------------------------------------

    private List<LedgerReports.CollectionRow> collections(List<Contribution> contributions, List<Withdrawal> processed) {
        List<Withdrawal> engagedAll = withdrawalRepository.findAllForReports().stream()
            .filter(w -> w.getStatus() == WithdrawalStatus.PENDING || w.getStatus() == WithdrawalStatus.APPROVED)
            .toList();
        return projectRepository.findAll().stream()
            .map(p -> collection(p, contributions, processed, engagedAll))
            .sorted(Comparator.comparing(LedgerReports.CollectionRow::firstMovementDate,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.project().name()))
            .toList();
    }

    private LedgerReports.CollectionRow collection(Project p, List<Contribution> contributions, List<Withdrawal> processed,
                                                   List<Withdrawal> engagedAll) {
        Long id = p.getId();
        List<Contribution> in = contributions.stream().filter(c -> c.getProject() != null && id.equals(c.getProject().getId())).toList();
        List<Withdrawal> out = processed.stream().filter(w -> w.getProject() != null && id.equals(w.getProject().getId())).toList();
        Predicate<Contribution> collected = c -> MovementNature.COLLECTED.contains(c.getNature());
        long count = in.stream().filter(collected)
            .mapToLong(c -> c.getMovementType() == MovementType.ADJUSTMENT ? -1 : 1).sum();
        BigDecimal collectedAmount = sum(in.stream().filter(collected).map(Contribution::getAmount));
        BigDecimal other = sum(in.stream().filter(collected.negate()).map(Contribution::getAmount));
        BigDecimal used = sum(out.stream().map(Withdrawal::getAmount));
        BigDecimal engaged = sum(engagedAll.stream()
            .filter(w -> w.getProject() != null && id.equals(w.getProject().getId())).map(Withdrawal::getAmount));
        List<LocalDate> dates = Stream.concat(in.stream().map(Contribution::getTransactionDate), out.stream().map(this::date))
            .filter(Objects::nonNull).sorted().toList();
        return new LedgerReports.CollectionRow(
            ProjectRef.from(p), p.getStatus(), p.getDescription(),
            dates.isEmpty() ? p.getStartDate() : dates.get(0),
            dates.isEmpty() ? p.getEndDate() : dates.get(dates.size() - 1),
            count, collectedAmount, other, used,
            p.getOpeningBalance().add(collectedAmount).add(other).subtract(used),
            engaged);
    }

    /** Solde d'une catégorie (caisse) à une date donnée incluse, sur les dates réelles des mouvements. */
    private BigDecimal fundBalance(List<Contribution> contributions, List<Withdrawal> processed, Long categoryId, LocalDate until) {
        BigDecimal in = sum(contributions.stream()
            .filter(c -> c.getCategory() != null && categoryId.equals(c.getCategory().getId()))
            .filter(c -> !c.getTransactionDate().isAfter(until))
            .map(Contribution::getAmount));
        BigDecimal out = sum(processed.stream()
            .filter(w -> w.getCategory() != null && categoryId.equals(w.getCategory().getId()))
            .filter(w -> !date(w).isAfter(until))
            .map(Withdrawal::getAmount));
        return in.subtract(out);
    }

    private static List<Contribution> monthlyContributions(List<Contribution> contributions, int year) {
        return contributions.stream()
            .filter(c -> c.getNature() == MovementNature.MONTHLY_CONTRIBUTION)
            .filter(c -> c.getPeriodMonth().getYear() == year)
            .toList();
    }

    private static List<Withdrawal> processed(List<Withdrawal> withdrawals) {
        return withdrawals.stream().filter(w -> w.getStatus() == WithdrawalStatus.PROCESSED).toList();
    }

    /** Date réelle d'une sortie décaissée (date d'opération, à défaut celle du décaissement). */
    private LocalDate date(Withdrawal w) {
        if (w.getOperationDate() != null) {
            return w.getOperationDate();
        }
        Instant at = w.getProcessedAt() != null ? w.getProcessedAt() : w.getRequestedAt();
        return at.atZone(ZONE).toLocalDate();
    }

    private static Totals totals(List<Contribution> monthly) {
        Map<Long, BigDecimal> resp = new HashMap<>();
        Map<Long, BigDecimal> members = new HashMap<>();
        BigDecimal unattributed = BigDecimal.ZERO;
        for (Contribution c : monthly) {
            Donor d = c.getDonor();
            if (d == null) {
                unattributed = unattributed.add(c.getAmount());
            } else if (d.getMemberRole() == MemberRole.RESPONSABLE) {
                resp.merge(d.getId(), c.getAmount(), BigDecimal::add);
            } else {
                members.merge(d.getId(), c.getAmount(), BigDecimal::add);
            }
        }
        return new Totals(positive(resp), sum(resp.values().stream()), positive(members), sum(members.values().stream()),
            unattributed);
    }

    private record Totals(long respContributors, BigDecimal respAmount, long memberContributors, BigDecimal memberAmount,
                          BigDecimal unattributed) {
        BigDecimal total() {
            return respAmount.add(memberAmount).add(unattributed);
        }

        BigDecimal share() {
            BigDecimal total = total();
            return total.signum() == 0 ? null : respAmount.divide(total, 4, RoundingMode.HALF_UP);
        }
    }

    /** Nombre de personnes dont le total net du mois est positif (une contribution annulée ne compte pas). */
    private static long positive(Map<Long, BigDecimal> amounts) {
        Set<Long> ids = new HashSet<>();
        amounts.forEach((id, amount) -> {
            if (amount.signum() > 0) {
                ids.add(id);
            }
        });
        return ids.size();
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] a = new BigDecimal[12];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }
}
