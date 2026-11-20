package org.mla.finance.repository;

import jakarta.persistence.LockModeType;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderReason;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReminderCaseRepository extends JpaRepository<ReminderCase, Long> {

    Optional<ReminderCase> findByDonorIdAndReasonAndCaseKey(Long donorId, ReminderReason reason, String caseKey);

    /** Verrou de ligne avant toute transition : deux clics simultanés sur « Relancer » → une seule relance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReminderCase c where c.id = :id")
    Optional<ReminderCase> findByIdForUpdate(@Param("id") Long id);

    /** Même recherche que ci-dessus, sous verrou de ligne (état toujours relu frais, jamais celui d'un cache). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReminderCase c where c.donor.id = :donorId and c.reason = :reason and c.caseKey = :caseKey")
    Optional<ReminderCase> findForUpdate(@Param("donorId") Long donorId, @Param("reason") ReminderReason reason,
                                         @Param("caseKey") String caseKey);

    @EntityGraph(attributePaths = {"donor"})
    List<ReminderCase> findByReasonAndPeriodMonth(ReminderReason reason, LocalDate periodMonth);

    List<ReminderCase> findByDonorIdAndReasonAndPeriodMonthAndStatusNotIn(
        Long donorId, ReminderReason reason, LocalDate periodMonth, Collection<ReminderCaseStatus> statuses);

    /** Dossiers mensuels encore ouverts d'un mois terminé : passés « Sans suite » par le job quotidien. */
    List<ReminderCase> findByReasonAndPeriodMonthBeforeAndStatusNotIn(
        ReminderReason reason, LocalDate periodMonth, Collection<ReminderCaseStatus> statuses);
}
