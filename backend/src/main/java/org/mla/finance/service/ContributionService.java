package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.Project;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ProjectRepository;
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

@Service
@RequiredArgsConstructor
@Transactional
public class ContributionService {

    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.DESC, "transactionDate", "id");

    private final ContributionRepository contributionRepository;
    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final DonorRepository donorRepository;
    private final PaymentChannelRepository paymentChannelRepository;
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
        if ((request.projectId() == null) == (request.categoryId() == null)) {
            throw new BusinessRuleViolationException("RG-02",
                "Une contribution doit être rattachée à un projet OU à une catégorie de don (exactement un des deux).");
        }

        Project project = request.projectId() != null
            ? projectRepository.findById(request.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + request.projectId()))
            : null;
        DonationCategory category = request.categoryId() != null
            ? categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Catégorie introuvable : " + request.categoryId()))
            : null;
        PaymentChannel channel = paymentChannelRepository.findById(request.paymentChannelId())
            .orElseThrow(() -> new EntityNotFoundException("Canal de paiement introuvable : " + request.paymentChannelId()));
        if (!channel.isActive()) {
            throw new BusinessRuleViolationException("RG-15", "Le canal de paiement '" + channel.getLabel() + "' est désactivé.");
        }

        Contribution contribution = Contribution.builder()
            .project(project)
            .category(category)
            .donor(findDonor(request.donorId()))
            .paymentChannel(channel)
            .amount(request.amount())
            .periodMonth(request.periodMonth().atDay(1)) // RG-03 : normalisé au 1er du mois
            .transactionDate(request.transactionDate())
            .movementType(MovementType.STANDARD)
            .anonymous(request.anonymous())
            .notes(request.notes())
            // createdBy reste null tant que l'authentification réelle n'est pas branchée.
            .build();
        Contribution saved = contributionRepository.save(contribution);
        String envelope = project != null ? project.getName() : category.getName();
        auditLogService.record("CONTRIBUTION", saved.getId(), "CREATE",
            "Contribution de " + saved.getAmount() + " FCFA enregistrée pour " + envelope);
        return ContributionResponse.from(saved);
    }

    public ContributionResponse update(Long id, ContributionUpdateRequest request) {
        if (request.touchesFinancialFields()) {
            throw new BusinessRuleViolationException("RG-17",
                "Le montant, l'imputation, le canal et les dates d'une contribution ne sont pas modifiables : "
                    + "annulez-la via POST /api/v1/contributions/{id}/cancel puis recréez-la.");
        }
        Contribution contribution = find(id);
        contribution.setDonor(findDonor(request.donorId()));
        contribution.setAnonymous(Boolean.TRUE.equals(request.anonymous()));
        contribution.setNotes(request.notes());
        return ContributionResponse.from(contribution);
    }

    /** Annulation par écriture d'ajustement inverse : rien n'est supprimé ni modifié (RG-17). */
    public ContributionResponse cancel(Long id, CancelRequest request) {
        Contribution original = find(id);
        if (original.getMovementType() == MovementType.ADJUSTMENT) {
            throw new BusinessRuleViolationException("RG-17", "Une écriture d'ajustement ne peut pas être annulée.");
        }
        if (contributionRepository.existsByAdjustsContributionId(id)) {
            throw new ConflictException("La contribution " + id + " est déjà annulée.");
        }

        LocalDate today = LocalDate.now();
        Contribution adjustment = Contribution.builder()
            .project(original.getProject())
            .category(original.getCategory())
            .paymentChannel(original.getPaymentChannel())
            .amount(original.getAmount().negate())
            .periodMonth(today.withDayOfMonth(1))
            .transactionDate(today)
            .movementType(MovementType.ADJUSTMENT)
            .notes(request.reason())
            .adjustsContribution(original)
            .build();
        Contribution saved = contributionRepository.save(adjustment);
        auditLogService.record("CONTRIBUTION", saved.getId(), "CANCEL",
            "Annulation de la contribution #" + original.getId() + " par écriture d'ajustement #" + saved.getId());
        return ContributionResponse.from(saved);
    }

    private Contribution find(Long id) {
        return contributionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Contribution introuvable : " + id));
    }

    private Donor findDonor(Long donorId) {
        if (donorId == null) {
            return null;
        }
        return donorRepository.findById(donorId)
            .orElseThrow(() -> new EntityNotFoundException("Donateur introuvable : " + donorId));
    }
}
