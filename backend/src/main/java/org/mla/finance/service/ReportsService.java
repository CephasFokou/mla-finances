package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.AuditLogResponse;
import org.mla.finance.web.dto.MonthlyAmount;
import org.mla.finance.web.dto.ProjectMonthlyReportResponse;
import org.mla.finance.web.dto.ProjectRef;
import org.mla.finance.web.dto.ReportsOverviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Périmètre retenu pour "Rapports" (à documenter/confirmer avec Cephas, voir
 * claude/module-rapports-spec.md) : pas de génération asynchrone de rapports (PDF/email), pas de
 * planification/envoi automatique — ces briques (file d'attente de jobs, bibliothèque PDF,
 * intégration email) n'existent pas encore dans le projet. À la place : un aperçu global
 * synchrone (remplace les indicateurs d'exemple du Dashboard), une répartition mensuelle par
 * projet pour un graphique simple, et le journal d'audit réel (voir AuditLogService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportsService {

    private static final Set<WithdrawalStatus> PROCESSED_STATUS = EnumSet.of(WithdrawalStatus.PROCESSED);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ContributionRepository contributionRepository;
    private final ProjectRepository projectRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final AuditLogService auditLogService;

    public ReportsOverviewResponse overview() {
        BigDecimal globalBalance = projectRepository.sumOpeningBalance()
            .add(contributionRepository.sumAmountAll())
            .subtract(withdrawalRepository.sumAmountByStatusIn(PROCESSED_STATUS));

        LocalDate today = LocalDate.now(ZONE);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        BigDecimal mobilizedThisMonth = contributionRepository.sumAmountByTransactionDateBetween(firstOfMonth, today);

        Instant now = Instant.now();
        BigDecimal disbursedThisMonth = withdrawalRepository
            .sumAmountByStatusAndOperationDateBetween(WithdrawalStatus.PROCESSED, firstOfMonth, today);
        long pendingWithdrawalsCount = withdrawalRepository.countByStatus(WithdrawalStatus.PENDING);

        return new ReportsOverviewResponse(globalBalance, mobilizedThisMonth, disbursedThisMonth, pendingWithdrawalsCount, now);
    }

    /**
     * Répartition mensuelle d'un projet sur les {@code months} derniers mois (mois courant inclus).
     * Regroupement fait en mémoire (volumes attendus faibles par projet) plutôt qu'en base : évite
     * d'introduire une fonction SQL de troncature de date spécifique au moteur, alors que le projet
     * tourne aussi bien sur H2 (dev) qu'un futur moteur de prod non encore choisi.
     */
    public ProjectMonthlyReportResponse projectMonthly(Long projectId, int months) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + projectId));

        List<Contribution> contributions = contributionRepository.findByProjectId(projectId);
        List<Withdrawal> withdrawals = withdrawalRepository.findByProjectId(projectId);

        YearMonth currentMonth = YearMonth.now(ZONE);
        List<MonthlyAmount> series = new ArrayList<>();
        for (int offset = months - 1; offset >= 0; offset--) {
            YearMonth targetMonth = currentMonth.minusMonths(offset);

            BigDecimal contributionsTotal = contributions.stream()
                .filter(c -> YearMonth.from(c.getPeriodMonth()).equals(targetMonth))
                .map(Contribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal withdrawalsTotal = withdrawals.stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.PROCESSED && (w.getOperationDate() != null || w.getProcessedAt() != null))
                .filter(w -> (w.getOperationDate() != null
                    ? YearMonth.from(w.getOperationDate())
                    : YearMonth.from(w.getProcessedAt().atZone(ZONE))).equals(targetMonth))
                .map(Withdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            series.add(new MonthlyAmount(targetMonth.atDay(1), contributionsTotal, withdrawalsTotal));
        }

        return new ProjectMonthlyReportResponse(ProjectRef.from(project), series);
    }

    public Page<AuditLogResponse> auditLog(AuditLogFilter filter, Pageable pageable) {
        return auditLogService.search(filter, pageable);
    }
}
