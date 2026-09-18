package org.mla.finance.repository;

import org.mla.finance.domain.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {
    List<Contribution> findByProjectId(Long projectId);
    List<Contribution> findByCategoryId(Long categoryId);
}
