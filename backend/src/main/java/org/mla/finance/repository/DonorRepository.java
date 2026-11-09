package org.mla.finance.repository;

import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberRole;
import org.mla.finance.domain.MemberStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    /** Recherche pour les listes déroulantes : nom usuel ou nom complet, insensible à la casse. */
    @Query("""
        select d from Donor d
        where :q = '' or lower(coalesce(d.displayName, '')) like lower(concat('%', :q, '%'))
           or lower(coalesce(d.fullName, '')) like lower(concat('%', :q, '%'))
        order by case when d.memberRole is null then 1 else 0 end,
                 lower(coalesce(d.displayName, d.fullName))
        """)
    List<Donor> search(String q, Pageable pageable);

    List<Donor> findAllByOrderByDisplayNameAscFullNameAsc();

    long countByMemberRoleAndMemberStatus(MemberRole role, MemberStatus status);

    Optional<Donor> findByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, Long id);

    // Dédoublonnage des donateurs issus des dons publics (voir PaymentTransactionService.resolveDonor) :
    // on ne rapproche que sur un contact exact (email ou téléphone), jamais sur le nom seul.
    Optional<Donor> findByEmailIgnoreCase(String email);

    Optional<Donor> findByPhone(String phone);
}
