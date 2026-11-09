package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.web.dto.MemberRequest;
import org.mla.finance.web.dto.MemberResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Annuaire des personnes : membres du MLA (responsables et membres, onglet Membres) et tiers
 * (donateurs externes). Une personne n'est jamais supprimée : ses mouvements la référencent.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final DonorRepository donorRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        return donorRepository.findAll().stream()
            .sorted(Comparator
                .comparing((Donor d) -> d.getMemberRole() == null ? 2 : d.getMemberRole().ordinal())
                .thenComparing(d -> d.getLabel() == null ? "" : d.getLabel().toLowerCase()))
            .map(MemberResponse::from)
            .toList();
    }

    public MemberResponse create(MemberRequest request) {
        String displayName = request.displayName().trim();
        if (donorRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("Une personne nommée « " + displayName + " » existe déjà.");
        }
        Donor donor = new Donor();
        apply(donor, request, displayName);
        Donor saved = donorRepository.save(donor);
        auditLogService.record("MEMBER", saved.getId(), "CREATE", "Personne « " + saved.getLabel() + " » ajoutée");
        return MemberResponse.from(saved);
    }

    public MemberResponse update(Long id, MemberRequest request) {
        Donor donor = donorRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Personne introuvable : " + id));
        String displayName = request.displayName().trim();
        if (donorRepository.existsByDisplayNameIgnoreCaseAndIdNot(displayName, id)) {
            throw new ConflictException("Une personne nommée « " + displayName + " » existe déjà.");
        }
        apply(donor, request, displayName);
        auditLogService.record("MEMBER", id, "UPDATE", "Fiche de « " + donor.getLabel() + " » modifiée");
        return MemberResponse.from(donor);
    }

    private static void apply(Donor donor, MemberRequest request, String displayName) {
        donor.setDisplayName(displayName);
        donor.setFirstName(blankToNull(request.firstName()));
        donor.setLastName(blankToNull(request.lastName()));
        String fullName = String.join(" ",
            donor.getFirstName() != null ? donor.getFirstName() : "",
            donor.getLastName() != null ? donor.getLastName() : "").trim();
        donor.setFullName(fullName.isEmpty() ? displayName : fullName);
        donor.setMemberRole(request.memberRole());
        // Un tiers n'a pas de statut de membre ; un membre sans statut précisé est actif.
        donor.setMemberStatus(request.memberRole() == null ? null
            : request.memberStatus() != null ? request.memberStatus() : MemberStatus.ACTIVE);
        donor.setEmail(blankToNull(request.email()));
        donor.setPhone(blankToNull(request.phone()));
        donor.setRemark(blankToNull(request.remark()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
