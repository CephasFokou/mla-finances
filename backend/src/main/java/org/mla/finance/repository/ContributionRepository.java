package org.mla.finance.repository;

import org.mla.finance.domain.Contribution;
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

    /** "Mobilisé ce mois" (KPI Rapports/Dashboard) : bornes inclusives sur la date réelle de l'opération. */
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.transactionDate between :from and :to")
    BigDecimal sumAmountByTransactionDateBetween(LocalDate from, LocalDate to);

    // Évite le N+1 lors de la conversion en réponse (relations LAZY).
    @Override
    @EntityGraph(attributePaths = {"project", "category", "donor", "paymentChannel"})
    Page<Contribution> findAll(Specification<Contribution> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project", "category", "donor", "paymentChannel"})
    List<Contribution> findAll(Specification<Contribution> spec, Sort sort);
}
