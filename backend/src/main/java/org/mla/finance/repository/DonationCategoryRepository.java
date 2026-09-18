package org.mla.finance.repository;

import org.mla.finance.domain.DonationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationCategoryRepository extends JpaRepository<DonationCategory, Long> {
}
