package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MovementNature;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.DESC, "requestedAt", "id");

    private final WithdrawalRepository withdrawalRepository;
    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final LedgerLookup lookup;
    private final CurrentUserProvider currentUserProvider;
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

    @Transactional(readOnly = true)
    public List<Withdrawal> searchAll(WithdrawalFilter filter) {
        return withdrawalRepository.findAll(WithdrawalSpecifications.matching(filter), EXPORT_SORT);
    }

    /** Demande de retrait : passe ensuite par la validation puis le décaissement. */
    public WithdrawalResponse create(WithdrawalCreateRequest request) {
        Withdrawal withdrawal = build(request);
        withdrawal.setStatus(WithdrawalStatus.PENDING);
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        auditLogService.record("WITHDRAWAL", saved.getId(), "CREATE",
            "Demande de retrait (" + saved.getNature().label() + ") de " + saved.getAmount() + " FCFA sur "
                + envelopeName(saved));
        return WithdrawalResponse.from(saved);
    }

    /**
     * Enregistre une sortie déjà effectuée (dépense payée, enveloppe remise...) : directement
     * PROCESSED à la date réelle indiquée. Réservé, côté contrôleur, à qui peut à la fois demander et
     * valider un retrait — c'est la saisie a posteriori du cahier de caisse.
     */
    public WithdrawalResponse record(WithdrawalCreateRequest request) {
        if (request.operationDate() == null) {
            throw new BusinessRuleViolationException("OPERATION_DATE",
                "La date de la sortie est obligatoire pour enregistrer une dépense déjà effectuée.");
        }
        Withdrawal withdrawal = build(request);
        lockEnvelope(withdrawal);
        Instant now = Instant.now();
        withdrawal.setStatus(WithdrawalStatus.PROCESSED);
        withdrawal.setDecidedBy(withdrawal.getRequestedBy());
        withdrawal.setDecidedAt(now);
        withdrawal.setProcessedAt(now);
        withdrawal.setOperationDate(request.operationDate());
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        auditLogService.record("WITHDRAWAL", saved.getId(), "RECORD",
            saved.getNature().label() + " de " + saved.getAmount() + " FCFA enregistrée sur " + envelopeName(saved)
                + " (sortie du " + request.operationDate() + ")");
        return WithdrawalResponse.from(saved);
    }

    private Withdrawal build(WithdrawalCreateRequest request) {
        // Même principe que RG-02 sur les contributions : rattachement exclusif à une enveloppe.
        LedgerLookup.Envelope envelope = lookup.envelope(request.projectId(), request.categoryId(), "WITHDRAWAL_ENVELOPE",
            "Une demande de retrait doit être rattachée à un projet OU à une catégorie de don (exactement un des deux).");
        MovementNature nature = request.nature() != null ? request.nature() : MovementNature.EXPENSE;
        if (!nature.isOutflow()) {
            throw new BusinessRuleViolationException("NATURE",
                "« " + nature.label() + " » est une entrée d'argent : enregistrez-la comme contribution.");
        }
        if (nature == MovementNature.TRANSFER) {
            throw new BusinessRuleViolationException("RG-10",
                "Un transfert interne s'enregistre via POST /api/v1/transfers (les deux écritures sont créées ensemble).");
        }
        if (request.activityId() != null && envelope.category() == null) {
            throw new BusinessRuleViolationException("ACTIVITY",
                "Une activité se rattache aux mouvements de la caisse, pas à ceux d'une collecte.");
        }
        return Withdrawal.builder()
            .project(envelope.project())
            .category(envelope.category())
            .amount(request.amount())
            .reason(request.reason())
            .nature(nature)
            .person(lookup.donorOrNull(request.personId()))
            .paymentChannel(lookup.activeChannelOrNull(request.paymentChannelId()))
            .activity(lookup.activityOrNull(request.activityId()))
            .remark(request.remark())
            .requestedBy(currentUserProvider.currentOrNull())
            .build();
    }

    public WithdrawalResponse approve(Long id) {
        Withdrawal withdrawal = find(id);
        requireStatus(withdrawal, WithdrawalStatus.PENDING, "valider");
        lockEnvelope(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setDecidedAt(Instant.now());
        withdrawal.setDecidedBy(currentUserProvider.currentOrNull());
        auditLogService.record("WITHDRAWAL", id, "APPROVE", "Retrait #" + id + " validé");
        return WithdrawalResponse.from(withdrawal);
    }

    public WithdrawalResponse reject(Long id, WithdrawalRejectRequest request) {
        Withdrawal withdrawal = find(id);
        requireStatus(withdrawal, WithdrawalStatus.PENDING, "rejeter");
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setDecidedAt(Instant.now());
        withdrawal.setDecidedBy(currentUserProvider.currentOrNull());
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
        withdrawal.setOperationDate(LocalDate.now());
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

    private static String envelopeName(Withdrawal w) {
        return w.getProject() != null ? w.getProject().getName() : w.getCategory().getName();
    }

    private Withdrawal find(Long id) {
        return withdrawalRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Retrait introuvable : " + id));
    }
}
