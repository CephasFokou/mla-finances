package org.mla.finance.repository;

import org.mla.finance.domain.ReminderSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, Long> {
}
