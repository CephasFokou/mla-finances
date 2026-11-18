package org.mla.finance.repository;

import org.mla.finance.domain.MonthlyContributionSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyContributionSettingsRepository extends JpaRepository<MonthlyContributionSettings, Long> {
}
