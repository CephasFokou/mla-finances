package org.mla.finance.repository;

import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.MovementNature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ContributionRepository extends JpaRepository<Contribution, Long>, JpaSpecificationExecutor<Contribution> {
    List<Contribution> findByProjectId(Long projectId);
    List<Contribution> findByCategoryId(Long categoryId);

    boolean existsByAdjustsContributionId(Long adjustsContributionId);

    long countByProjectId(Long projectId);
    long countByCategoryId(Long categoryId);

    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.project.id = :projectId")
    BigDecimal sumAmountByProjectId(Long projectId);

    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.category.id = :categoryId")
    BigDecimal sumAmountByCategoryId(Long categoryId);

    /** Utilisé par ReportsService pour le solde global (toutes enveloppes confondues). */
    @Query("select coalesce(sum(c.amount), 0) from Contribution c")
    BigDecimal sumAmountAll();

    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.project is not null and c.nature in :natures")
    BigDecimal sumCollectedForProjects(Collection<MovementNature> natures);

    @Query("select count(c) from Contribution c where c.project is not null and c.nature in :natures and c.movementType = org.mla.finance.domain.MovementType.STANDARD")
    long countCollectedForProjects(Collection<MovementNature> natures);

    /** "Mobilisé ce mois" (KPI Rapports/Dashboard) : bornes inclusives sur la date réelle de l'opération. */
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.transactionDate between :from and :to")
    BigDecimal sumAmountByTransactionDateBetween(LocalDate from, LocalDate to);

    /** Toutes les écritures, relations chargées : base des rapports calculés en mémoire (volumes faibles). */
    @EntityGraph(attributePaths = {"project", "category", "donor", "activity"})
    @Query("select c from Contribution c")
    List<Contribution> findAllForReports();

    // Évite le N+1 lors de la conversion en réponse (relations LAZY).
    @Override
    @EntityGraph(attributePaths = {"project", "category", "donor", "paymentChannel", "activity"})
    Page<Contribution> findAll(Specification<Contribution> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project", "category", "donor", "paymentChannel", "activity"})
    List<Contribution> findAll(Specification<Contribution> spec, Sort sort);

    /**
     * Mois déjà payés par un membre (contributions mensuelles, ajustements déduits), hors celles
     * créées par le don en ligne {@code excludeTransactionId} : sert à avertir le secrétariat d'un
     * doublon possible, jamais à refuser un paiement.
     */
    @Query("""
        select c.periodMonth from Contribution c left join c.paymentTransaction pt
        where c.donor.id = :donorId
          and c.nature = org.mla.finance.domain.MovementNature.MONTHLY_CONTRIBUTION
          and c.periodMonth in :months
          and (pt is null or pt.id <> :excludeTransactionId)
        group by c.periodMonth
        having sum(c.amount) > 0
        """)
    List<LocalDate> paidMonths(Long donorId, Collection<LocalDate> months, Long excludeTransactionId);

    /** Membres dont la contribution mensuelle du mois est enregistrée (ajustements déduits) : base des relances. */
    @Query("""
        select c.donor.id from Contribution c
        where c.donor is not null
          and c.nature = org.mla.finance.domain.MovementNature.MONTHLY_CONTRIBUTION
          and c.periodMonth = :month
        group by c.donor.id
        having sum(c.amount) > 0
        """)
    List<Long> donorsWithMonthlyContribution(@Param("month") LocalDate month);

    /** Écritures mensuelles d'un membre pour un mois (la dernière positive clôt le dossier de relance). */
    @Query("""
        select c from Contribution c
        where c.donor.id = :donorId
          and c.nature = org.mla.finance.domain.MovementNature.MONTHLY_CONTRIBUTION
          and c.periodMonth = :month
        order by c.id desc
        """)
    List<Contribution> monthlyEntries(@Param("donorId") Long donorId, @Param("month") LocalDate month);

    /** Membres ayant donné à une collecte (ajustements déduits) : un don, quel que soit son montant, clôt la relance. */
    @Query("""
        select c.donor.id from Contribution c
        where c.donor is not null and c.project.id = :projectId
        group by c.donor.id
        having sum(c.amount) > 0
        """)
    List<Long> donorsWithProjectContribution(@Param("projectId") Long projectId);

    /** Dons d'un membre à une collecte, le plus récent d'abord. */
    @Query("select c from Contribution c where c.donor.id = :donorId and c.project.id = :projectId order by c.id desc")
    List<Contribution> projectEntries(@Param("donorId") Long donorId, @Param("projectId") Long projectId);
}
