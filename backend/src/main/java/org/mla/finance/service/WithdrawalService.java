package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.WithdrawalCreateRequest;
import org.mla.finance.web.dto.WithdrawalRejectRequest;
import org.mla.finance.web.dto.WithdrawalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Workflow retrait : demande (PENDING) -> validation (APPROVED/REJECTED, un rôle distinct du
 * demandeur via VALIDATE_WITHDRAWALS) -> décaissement effectif (PROCESSED, MANAGE_WITHDRAWALS).
 * Annulation possible tant que le retrait n'est pas décaissé (PENDING ou APPROVED -> CANCELLED).
 *
 * Anti-concurrence : approve()/process() verrouillent (PESSIMISTIC_WRITE) la ligne de l'enveloppe
 * concernée avant de relire le solde engagé, pour que deux validations simultanées sur la même
 * enveloppe se sérialisent au lieu de valider en double au-delà du solde réel disponible.
 *
 * Alerte de déficit : la création et la validation d'un retrait ne sont PAS bloquées si le solde
 * prévisionnel de l'enveloppe devient négatif (le trésorier peut avoir une bonne raison d'engager
 * une dépense malgré un solde tendu) — le déficit reste seulement signalé dans
 * BalanceService/ProjectBalanceResponse. À confirmer avec Cephas si un blocage strict est
 * attendu à la place d'un simple signal.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> search(WithdrawalFilter filter, Pageable pageable) {
        return withdrawalRepository.findAll(WithdrawalSpecifications.matching(filter), pageable)
            .map(WithdrawalResponse::from);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse get(Long id) {
        return WithdrawalResponse.from(find(id));
    }

    public WithdrawalResponse create(WithdrawalCreateRequest request) {
        // Même principe que RG-02 sur les contributions : rattachement exclusif à une enveloppe.
        if ((request.projectId() == null) == (request.categoryId() == null)) {
            throw new BusinessRuleViolationException("WITHDRAWAL_ENVELOPE",
                "Une demande de retrait doit être rattachée à un projet OU à une catégorie de don (exactement un des deux).");
        }
        Project project = request.projectId() != null
            ? projectRepository.findById(request.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + request.projectId()))
            : null;
        DonationCategory category = request.categoryId() != null
            ? categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Catégorie introuvable : " + request.categoryId()))
            : null;

        Withdrawal withdrawal = Withdrawal.builder()
            .project(project)
            .category(category)
            .amount(request.amount())
            .reason(request.reason())
            .status(WithdrawalStatus.PENDING)
            // requestedBy reste null tant que l'authentification réelle n'est pas branchée.
            .build();
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        String envelope = project != null ? project.getName() : category.getName();
        auditLogService.record("WITHDRAWAL", saved.getId(), "CREATE",
            "Demande de retrait de " + saved.getAmount() + " FCFA sur " + envelope);
        return WithdrawalResponse.from(saved);
    }

    public WithdrawalResponse approve(Long id) {
        Withdrawal withdrawal = find(id);
        requireStatus(withdrawal, WithdrawalStatus.PENDING, "valider");
        lockEnvelope(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setDecidedAt(Instant.now());
        auditLogService.record("WITHDRAWAL", id, "APPROVE", "Retrait #" + id + " validé");
        return WithdrawalResponse.from(withdrawal);
    }

    public WithdrawalResponse reject(Long id, WithdrawalRejectRequest request) {
        Withdrawal withdrawal = find(id);
        requireStatus(withdrawal, WithdrawalStatus.PENDING, "rejeter");
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setDecidedAt(Instant.now());
        withdrawal.setRejectionReason(request.reason());
        auditLogService.record("WITHDRAWAL", id, "REJECT", "Retrait #" + id + " rejeté : " + request.reason());
        return WithdrawalResponse.from(withdrawal);
    }

    public WithdrawalResponse cancel(Long id) {
        Withdrawal withdrawal = find(id);
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
            throw new ConflictException(
                "Impossible d'annuler le retrait " + id + " : statut actuel " + withdrawal.getStatus() + ".");
        }
        withdrawal.setStatus(WithdrawalStatus.CANCELLED);
        auditLogService.record("WITHDRAWAL", id, "CANCEL", "Retrait #" + id + " annulé");
        return WithdrawalResponse.from(withdrawal);
    }

    /** Décaissement effectif : seul un retrait APPROVED peut être marqué PROCESSED. */
    public WithdrawalResponse process(Long id) {
        Withdrawal withdrawal = find(id);
        requireStatus(withdrawal, WithdrawalStatus.APPROVED, "décaisser");
        lockEnvelope(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.PROCESSED);
        withdrawal.setProcessedAt(Instant.now());
        auditLogService.record("WITHDRAWAL", id, "PROCESS", "Retrait #" + id + " décaissé");
        return WithdrawalResponse.from(withdrawal);
    }

    /** Verrouille la ligne Project/DonationCategory ciblée (anti-concurrence, voir javadoc de classe). */
    private void lockEnvelope(Withdrawal withdrawal) {
        if (withdrawal.getProject() != null) {
            projectRepository.findWithLockById(withdrawal.getProject().getId());
        } else if (withdrawal.getCategory() != null) {
            categoryRepository.findWithLockById(withdrawal.getCategory().getId());
        }
    }

    private void requireStatus(Withdrawal withdrawal, WithdrawalStatus expected, String action) {
        if (withdrawal.getStatus() != expected) {
            throw new ConflictException(
                "Impossible de " + action + " le retrait " + withdrawal.getId() + " : statut actuel "
                    + withdrawal.getStatus() + " (attendu " + expected + ").");
        }
    }

    private Withdrawal find(Long id) {
        return withdrawalRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Retrait introuvable : " + id));
    }
}
