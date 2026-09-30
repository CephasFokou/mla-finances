package org.mla.finance.repository;

import org.mla.finance.domain.Donor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonorRepository extends JpaRepository<Donor, Long> {
    List<Donor> findTop20ByOrderByFullNameAsc();
    List<Donor> findTop20ByFullNameContainingIgnoreCaseOrderByFullNameAsc(String fullName);
}
