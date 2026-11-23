package org.mla.finance.repository;

import org.mla.finance.domain.Reminder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByReminderCaseIdOrderBySequenceNoAsc(Long caseId);

    long countByReminderCaseId(Long caseId);

    List<Reminder> findByReminderCaseIdIn(Collection<Long> caseIds);

    /** Relances mises en file (hors plage horaire) dont l'heure d'envoi est arrivée. */
    @Query("select r.id from Reminder r where r.sentAt is null and r.cancelledAt is null "
        + "and r.scheduledFor is not null and r.scheduledFor <= :now order by r.scheduledFor")
    List<Long> findDueQueuedIds(@Param("now") Instant now, Pageable pageable);

    /** Dossiers ayant une réponse à lire (badge de la navigation). */
    @Query("select count(c) from ReminderCase c where c.needsAttention = true")
    long countNeedingAttention();

    @Query("select r from Reminder r where r.reminderCase.id = :caseId and r.sentAt is null and r.cancelledAt is null")
    List<Reminder> findQueuedByCase(@Param("caseId") Long caseId);
}
