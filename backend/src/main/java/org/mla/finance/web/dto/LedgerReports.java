package org.mla.finance.web.dto;

import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapports qui reprennent les onglets du fichier de suivi MLA-ICC-YDE-FINANCE-2026 : Synthèse,
 * Calendrier des contributions mensuelles, Collectes et Activités. Tous calculés à la volée (RG-07).
 */
public final class LedgerReports {

    private LedgerReports() {
    }

    /** Onglet « Synthèse ». */
    public record Summary(
        int year,
        CategoryRef mainFund,
        BigDecimal mainFundBalance,
        BigDecimal openCollectionsBalance,
        long openCollectionsCount,
        BigDecimal monthlyContributionsTotal,
        long activeResponsablesCount,
        long activeMembersCount,
        List<MonthSummary> months,
        YearSummary yearTotals,
        Instant generatedAt
    ) {
    }

    /**
     * Une ligne mensuelle : mois POUR LEQUEL on contribue (mois d'attribution, RG-03), pas date de
     * dépôt. {@code responsablesShare} = montant des responsables / total du mois (null si total nul).
     * {@code mainFundBalanceEndOfMonth} = solde de la caisse principale seule, à la date réelle.
     */
    public record MonthSummary(
        String month,
        long responsablesContributors,
        BigDecimal responsablesAmount,
        long membersContributors,
        BigDecimal membersAmount,
        BigDecimal unattributedAmount,
        BigDecimal total,
        BigDecimal responsablesShare,
        BigDecimal mainFundBalanceEndOfMonth
    ) {
    }

    public record YearSummary(
        long responsablesContributors,
        BigDecimal responsablesAmount,
        long membersContributors,
        BigDecimal membersAmount,
        BigDecimal unattributedAmount,
        BigDecimal total,
        BigDecimal responsablesShare
    ) {
    }

    /** Onglet « Calendrier » : 12 montants par membre, selon le mois d'attribution. */
    public record Calendar(int year, List<CalendarRow> rows, List<BigDecimal> unattributed, List<BigDecimal> totals) {
    }

    public record CalendarRow(
        Long donorId,
        String displayName,
        MemberRole memberRole,
        MemberStatus memberStatus,
        List<BigDecimal> amounts,
        BigDecimal total,
        int monthsCount
    ) {
    }

    /** Onglet « Collectes » : une ligne par collecte dédiée (projet), hors caisse principale. */
    public record CollectionRow(
        ProjectRef project,
        ProjectStatus status,
        String description,
        LocalDate firstMovementDate,
        LocalDate lastMovementDate,
        long contributionsCount,
        BigDecimal collected,
        BigDecimal otherInflows,
        BigDecimal used,
        BigDecimal remaining,
        BigDecimal engaged
    ) {
    }

    /** Onglet « Activités » : coût net pour la caisse de chaque activité. */
    public record ActivityRow(
        ActivityResponse activity,
        BigDecimal expenses,
        BigDecimal returns,
        BigDecimal netCost,
        long movementsCount
    ) {
    }
}
