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
}
