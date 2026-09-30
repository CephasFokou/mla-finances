package org.mla.finance.repository;

import jakarta.persistence.LockModeType;
import org.mla.finance.domain.DonationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DonationCategoryRepository extends JpaRepository<DonationCategory, Long> {
    List<DonationCategory> findByActiveTrueOrderByNameAsc();

    List<DonationCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Même principe que ProjectRepository.findWithLockById : sérialise les décisions de retrait
     * concurrentes sur la même catégorie de don. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DonationCategory c where c.id = :id")
    Optional<DonationCategory> findWithLockById(Long id);
}
