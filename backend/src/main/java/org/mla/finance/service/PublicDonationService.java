package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.payment.PaymentInitiationResult;
import org.mla.finance.payment.PaymentProvider;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.web.dto.PublicDonationRequest;
import org.mla.finance.web.dto.PublicDonationResponse;
import org.mla.finance.web.dto.PublicProjectResponse;
import org.mla.finance.web.dto.PublicStatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;

/**
 * Point d'entrée du front-office public (page de don) : lecture des projets actifs à soutenir et
 * création d'une intention de don (PaymentTransaction), jamais d'une Contribution directement —
 * voir la javadoc de PaymentTransaction pour pourquoi. Ne nécessite aucune authentification
 * (SecurityConfig exempte /api/v1/public/**). Les codes DON-xx utilisés ici sont des codes
 * d'erreur internes à ce module, pas des règles de gestion du Cahier des Charges (contrairement à
 * RG-15, repris tel quel car c'est bien la même règle que celle déjà appliquée côté back-office).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicDonationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projectRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ContributionRepository contributionRepository;
    private final PaymentProvider paymentProvider;

    public List<PublicProjectResponse> activeProjects() {
        return projectRepository.findByStatus(ProjectStatus.ACTIVE).stream()
            .map(this::toPublicResponse)
            .toList();
    }

    public PublicStatsResponse stats() {
        long activeProjectCount = projectRepository.findByStatus(ProjectStatus.ACTIVE).size();
        // Seules les sommes réellement collectées pour des collectes dédiées : ni la caisse interne
        // (contributions mensuelles), ni les mouvements techniques (transferts, avances, ajustements).
        return new PublicStatsResponse(
            contributionRepository.sumCollectedForProjects(MovementNature.COLLECTED),
            contributionRepository.countCollectedForProjects(MovementNature.COLLECTED),
            activeProjectCount);
    }

    @Transactional
    public PublicDonationResponse submit(PublicDonationRequest request) {
        Project project = projectRepository.findById(request.projectId())
            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + request.projectId()));
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessRuleViolationException("DON-01", "Ce projet n'accepte plus de dons pour le moment.");
        }
        PaymentChannel channel = paymentChannelRepository.findById(request.paymentChannelId())
            .orElseThrow(() -> new EntityNotFoundException("Canal de paiement introuvable : " + request.paymentChannelId()));
        if (!channel.isActive()) {
            throw new BusinessRuleViolationException("RG-15", "Le canal de paiement '" + channel.getLabel() + "' est désactivé.");
        }
        if (!request.anonymous() && (request.donorName() == null || request.donorName().isBlank())) {
            throw new BusinessRuleViolationException("DON-02", "Le nom du donateur est requis, sauf pour un don anonyme.");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
            .project(project)
            .paymentChannel(channel)
            .amount(request.amount())
            .anonymous(request.anonymous())
            .donorName(request.anonymous() ? null : request.donorName())
            .donorContact(request.anonymous() ? null : request.donorContact())
            .reference(generateReference())
            .status(PaymentTransactionStatus.PENDING)
            .build();

        PaymentInitiationResult result = paymentProvider.initiate(transaction);
        transaction.setProvider(paymentProvider.code());
        transaction.setProviderReference(result.providerReference());

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        return new PublicDonationResponse(saved.getReference(), saved.getStatus().name());
    }

    private PublicProjectResponse toPublicResponse(Project project) {
        BigDecimal collected = project.getOpeningBalance().add(contributionRepository.sumAmountByProjectId(project.getId()));
        Integer progress = null;
        if (project.getTargetAmount() != null && project.getTargetAmount().signum() > 0) {
            progress = collected.multiply(BigDecimal.valueOf(100))
                .divide(project.getTargetAmount(), 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .max(BigDecimal.ZERO)
                .intValue();
        }
        return new PublicProjectResponse(project.getId(), project.getName(), project.getDescription(),
            project.getTargetAmount(), collected, progress);
    }

    private static String generateReference() {
        return "MLA-" + (100000 + RANDOM.nextInt(900000));
    }
}
