package org.mla.finance.repository;

import org.mla.finance.domain.Donor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DonorRepository extends JpaRepository<Donor, Long> {
    List<Donor> findTop20ByOrderByFullNameAsc();
    List<Donor> findTop20ByFullNameContainingIgnoreCaseOrderByFullNameAsc(String fullName);

    // Dédoublonnage des donateurs issus des dons publics (voir PaymentTransactionService.resolveDonor) :
    // on ne rapproche que sur un contact exact (email ou téléphone), jamais sur le nom seul.
    Optional<Donor> findByEmailIgnoreCase(String email);
    Optional<Donor> findByPhone(String phone);
}
