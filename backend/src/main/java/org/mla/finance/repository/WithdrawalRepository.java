package org.mla.finance.repository;

import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long>, JpaSpecificationExecutor<Withdrawal> {

    List<Withdrawal> findByProjectId(Long projectId);

    @Query("select coalesce(sum(w.amount), 0) from Withdrawal w where w.project.id = :projectId and w.status in :statuses")
    BigDecimal sumAmountByProjectIdAndStatusIn(Long projectId, Collection<WithdrawalStatus> statuses);

    @Query("select coalesce(sum(w.amount), 0) from Withdrawal w where w.category.id = :categoryId and w.status in :statuses")
    BigDecimal sumAmountByCategoryIdAndStatusIn(Long categoryId, Collection<WithdrawalStatus> statuses);

    long countByProjectIdAndStatus(Long projectId, WithdrawalStatus status);

    long countByCategoryIdAndStatus(Long categoryId, WithdrawalStatus status);

    long countByStatus(WithdrawalStatus status);

    /** Utilisé par ReportsService pour le solde global (toutes enveloppes confondues). */
    @Query("select coalesce(sum(w.amount), 0) from Withdrawal w where w.status in :statuses")
    BigDecimal sumAmountByStatusIn(Collection<WithdrawalStatus> statuses);

    /** "Décaissé ce mois" (KPI Rapports/Dashboard) : bornes sur la date de décaissement effectif. */
    @Query("select coalesce(sum(w.amount), 0) from Withdrawal w where w.status = :status and w.processedAt between :from and :to")
    BigDecimal sumAmountByStatusAndProcessedAtBetween(WithdrawalStatus status, Instant from, Instant to);

    // Évite le N+1 lors de la conversion en réponse (relations LAZY).
    @Override
    @EntityGraph(attributePaths = {"project", "category", "requestedBy", "decidedBy"})
    Page<Withdrawal> findAll(Specification<Withdrawal> spec, Pageable pageable);
}
