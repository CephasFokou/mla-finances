package org.mla.finance.repository;

import jakarta.persistence.LockModeType;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(ProjectStatus status);

    List<Project> findByRecurringSeriesIdOrderByStartDateAsc(String recurringSeriesId);

    /** Verrou pessimiste utilisé lors de la validation/traitement d'un retrait (anti-concurrence :
     * deux décisions simultanées sur la même enveloppe sont sérialisées). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findWithLockById(Long id);

    /** Utilisé par ReportsService pour le solde global (somme des soldes d'ouverture de tous les projets). */
    @Query("select coalesce(sum(p.openingBalance), 0) from Project p")
    BigDecimal sumOpeningBalance();
}
