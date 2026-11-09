package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.User;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.ContributionResponse;
import org.mla.finance.web.dto.TransferRequest;
import org.mla.finance.web.dto.TransferResponse;
import org.mla.finance.web.dto.WithdrawalResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * RG-10 : transfert interne entre deux enveloppes (ex. reliquat d'une collecte reversé dans la
 * caisse, complément d'une collecte par une autre). Deux écritures liées par le même
 * {@code transferGroup}, créées dans la même transaction : une sortie décaissée (nature TRANSFER)
 * sur la source, une entrée (nature et type TRANSFER) sur la destination. Le total de l'argent
 * détenu ne change donc pas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransferService {

    private final WithdrawalRepository withdrawalRepository;
    private final ContributionRepository contributionRepository;
    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final LedgerLookup lookup;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;

    public TransferResponse create(TransferRequest request) {
        LedgerLookup.Envelope from = lookup.envelope(request.fromProjectId(), request.fromCategoryId(), "RG-10",
            "La source d'un transfert doit être un projet OU une catégorie (exactement un des deux).");
        LedgerLookup.Envelope to = lookup.envelope(request.toProjectId(), request.toCategoryId(), "RG-10",
            "La destination d'un transfert doit être un projet OU une catégorie (exactement un des deux).");
        if (from.sameAs(to)) {
            throw new BusinessRuleViolationException("RG-10", "La source et la destination d'un transfert doivent être différentes.");
        }
        if (request.activityId() != null && from.category() == null && to.category() == null) {
            throw new BusinessRuleViolationException("ACTIVITY",
                "Une activité se rattache aux mouvements de la caisse, pas à ceux d'une collecte.");
        }
        // Anti-concurrence : même verrou que pour un décaissement sur la source.
        if (from.project() != null) {
            projectRepository.findWithLockById(from.project().getId());
        } else {
            categoryRepository.findWithLockById(from.category().getId());
        }
        Activity activity = lookup.activityOrNull(request.activityId());
        PaymentChannel channel = lookup.otherChannel();
        User actor = currentUserProvider.currentOrNull();
        Instant now = Instant.now();
        String group = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Withdrawal outflow = withdrawalRepository.save(Withdrawal.builder()
            .project(from.project())
            .category(from.category())
            .amount(request.amount())
            .reason(request.reason() + " (vers " + to.name() + ")")
            .nature(MovementNature.TRANSFER)
            .paymentChannel(channel)
            .activity(from.category() != null ? activity : null)
            .status(WithdrawalStatus.PROCESSED)
            .requestedBy(actor)
            .decidedBy(actor)
            .requestedAt(now)
            .decidedAt(now)
            .processedAt(now)
            .operationDate(request.operationDate())
            .transferGroup(group)
            .build());
        Contribution inflow = contributionRepository.save(Contribution.builder()
            .project(to.project())
            .category(to.category())
            .paymentChannel(channel)
            .amount(request.amount())
            .periodMonth(YearMonth.from(request.operationDate()).atDay(1))
            .transactionDate(request.operationDate())
            .movementType(MovementType.TRANSFER)
            .nature(MovementNature.TRANSFER)
            .activity(to.category() != null ? activity : null)
            .notes(request.reason() + " (depuis " + from.name() + ")")
            .transferGroup(group)
            .createdBy(actor)
            .build());

        auditLogService.record("WITHDRAWAL", outflow.getId(), "TRANSFER",
            "Transfert " + group + " de " + request.amount() + " FCFA : " + from.name() + " → " + to.name());
        return new TransferResponse(group, WithdrawalResponse.from(outflow), ContributionResponse.from(inflow));
    }
}
