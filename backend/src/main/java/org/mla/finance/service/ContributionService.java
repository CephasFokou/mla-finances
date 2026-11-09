package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.ContributionRequest;
import org.mla.finance.web.dto.ContributionResponse;
import org.mla.finance.web.dto.ContributionUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Entrées d'argent (contributions, dons, retours, avances, régularisations). Les transferts internes
 * passent par {@link TransferService}, qui crée les deux écritures d'un coup.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContributionService {

    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.DESC, "transactionDate", "id");

    private final ContributionRepository contributionRepository;
    private final LedgerLookup lookup;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<ContributionResponse> search(ContributionFilter filter, Pageable pageable) {
        return contributionRepository.findAll(ContributionSpecifications.matching(filter), pageable)
            .map(ContributionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<Contribution> searchAll(ContributionFilter filter) {
        return contributionRepository.findAll(ContributionSpecifications.matching(filter), EXPORT_SORT);
    }

    @Transactional(readOnly = true)
    public ContributionResponse get(Long id) {
        return ContributionResponse.from(find(id));
    }

    public ContributionResponse create(ContributionRequest request) {
        // RG-02 : validé ici en plus de la contrainte CHECK en base (simple filet de sécurité).
        LedgerLookup.Envelope envelope = lookup.envelope(request.projectId(), request.categoryId(), "RG-02",
            "Une contribution doit être rattachée à un projet OU à une catégorie de don (exactement un des deux).");
        MovementNature nature = request.nature() != null ? request.nature() : MovementNature.CONTRIBUTION;
        if (!nature.isInflow()) {
            throw new BusinessRuleViolationException("NATURE",
                "« " + nature.label() + " » est une sortie d'argent : enregistrez-la comme retrait.");
        }
        if (nature == MovementNature.TRANSFER) {
            throw new BusinessRuleViolationException("RG-10",
                "Un transfert interne s'enregistre via POST /api/v1/transfers (les deux écritures sont créées ensemble).");
        }
        if (nature == MovementNature.MONTHLY_CONTRIBUTION
            && (envelope.category() == null || !envelope.category().isMainFund())) {
            throw new BusinessRuleViolationException("MONTHLY_CONTRIBUTION",
                "Une contribution mensuelle va toujours dans la caisse principale du MLA.");
        }
        Activity activity = activityFor(envelope, request.activityId());
        PaymentChannel channel = lookup.activeChannel(request.paymentChannelId());

        Contribution contribution = Contribution.builder()
            .project(envelope.project())
            .category(envelope.category())
            .donor(lookup.donorOrNull(request.donorId()))
            .paymentChannel(channel)
            .amount(request.amount())
            .periodMonth(request.periodMonth().atDay(1)) // RG-03 : normalisé au 1er du mois
            .transactionDate(request.transactionDate())
            .movementType(MovementType.STANDARD)
            .nature(nature)
            .activity(activity)
            .anonymous(request.anonymous())
            .notes(request.notes())
            .remark(request.remark())
            .createdBy(currentUserProvider.currentOrNull())
            .build();
        Contribution saved = contributionRepository.save(contribution);
        auditLogService.record("CONTRIBUTION", saved.getId(), "CREATE",
            nature.label() + " de " + saved.getAmount() + " FCFA enregistrée pour " + envelope.name());
        return ContributionResponse.from(saved);
    }

    public ContributionResponse update(Long id, ContributionUpdateRequest request) {
        if (request.touchesFinancialFields()) {
            throw new BusinessRuleViolationException("RG-17",
                "Le montant, l'imputation, le canal et les dates d'une contribution ne sont pas modifiables : "
                    + "annulez-la via POST /api/v1/contributions/{id}/cancel puis recréez-la.");
        }
        Contribution contribution = find(id);
        contribution.setDonor(lookup.donorOrNull(request.donorId()));
        contribution.setAnonymous(Boolean.TRUE.equals(request.anonymous()));
        contribution.setNotes(request.notes());
        contribution.setRemark(request.remark());
        contribution.setActivity(activityFor(
            new LedgerLookup.Envelope(contribution.getProject(), contribution.getCategory()), request.activityId()));
        return ContributionResponse.from(contribution);
    }

    /**
     * Annulation par écriture d'ajustement inverse : rien n'est supprimé ni modifié (RG-17).
     * L'ajustement reprend le mois d'attribution, la nature, la personne et l'activité de l'original,
     * pour que les rapports par mois et par membre (calendrier des contributions) retombent juste ;
     * seule la date réelle de l'opération est celle du jour.
     */
    public ContributionResponse cancel(Long id, CancelRequest request) {
        Contribution original = find(id);
        if (original.getMovementType() == MovementType.ADJUSTMENT) {
            throw new BusinessRuleViolationException("RG-17", "Une écriture d'ajustement ne peut pas être annulée.");
        }
        if (original.getTransferGroup() != null) {
            throw new BusinessRuleViolationException("RG-10",
                "Cette écriture fait partie d'un transfert interne : enregistrez un transfert inverse pour le corriger.");
        }
        if (contributionRepository.existsByAdjustsContributionId(id)) {
            throw new ConflictException("La contribution " + id + " est déjà annulée.");
        }
        Contribution adjustment = Contribution.builder()
            .project(original.getProject())
            .category(original.getCategory())
            .donor(original.getDonor())
            .paymentChannel(original.getPaymentChannel())
            .amount(original.getAmount().negate())
            .periodMonth(original.getPeriodMonth())
            .transactionDate(LocalDate.now())
            .movementType(MovementType.ADJUSTMENT)
            .nature(original.getNature())
            .activity(original.getActivity())
            .anonymous(original.isAnonymous())
            .notes(request.reason())
            .adjustsContribution(original)
            .createdBy(currentUserProvider.currentOrNull())
            .build();
        Contribution saved = contributionRepository.save(adjustment);
        auditLogService.record("CONTRIBUTION", saved.getId(), "CANCEL",
            "Annulation de la contribution #" + original.getId() + " par écriture d'ajustement #" + saved.getId());
        return ContributionResponse.from(saved);
    }

    /** Une activité se rattache uniquement à un mouvement de la caisse (catégorie), jamais d'une collecte. */
    private Activity activityFor(LedgerLookup.Envelope envelope, Long activityId) {
        if (activityId != null && envelope.category() == null) {
            throw new BusinessRuleViolationException("ACTIVITY",
                "Une activité se rattache aux mouvements de la caisse, pas à ceux d'une collecte.");
        }
        return lookup.activityOrNull(activityId);
    }

    private Contribution find(Long id) {
        return contributionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Contribution introuvable : " + id));
    }
}
