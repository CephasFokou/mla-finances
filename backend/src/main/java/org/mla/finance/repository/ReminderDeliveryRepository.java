package org.mla.finance.repository;

import org.mla.finance.domain.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, Long> {

    List<ReminderDelivery> findByReminderIdInOrderByIdAsc(Collection<Long> reminderIds);
}
