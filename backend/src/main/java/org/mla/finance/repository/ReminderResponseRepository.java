package org.mla.finance.repository;

import org.mla.finance.domain.ReminderResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReminderResponseRepository extends JpaRepository<ReminderResponse, Long> {

    boolean existsByProviderMessageId(String providerMessageId);

    List<ReminderResponse> findByReminderCaseIdOrderByReceivedAtAsc(Long caseId);

    List<ReminderResponse> findByReminderCaseIdIn(Collection<Long> caseIds);
}
