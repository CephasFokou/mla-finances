package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Activity;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.Project;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.repository.ActivityRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ProjectRepository;
import org.springframework.stereotype.Component;

/**
 * Résolution des références communes aux entrées, sorties et transferts (enveloppe, personne,
 * canal, activité), avec les mêmes messages d'erreur partout.
 */
@Component
@RequiredArgsConstructor
public class LedgerLookup {

    /** Code du canal « Autre », seedé par la migration 0012 : utilisé pour les transferts internes. */
    public static final String OTHER_CHANNEL_CODE = "OTHER";

    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final DonorRepository donorRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final ActivityRepository activityRepository;

    /** Une enveloppe : exactement un projet (collecte) OU une catégorie (caisse). */
    public record Envelope(Project project, DonationCategory category) {
        public String name() {
            return project != null ? project.getName() : category.getName();
        }

        public boolean sameAs(Envelope other) {
            return project != null
                ? other.project != null && project.getId().equals(other.project.getId())
                : other.category != null && category.getId().equals(other.category.getId());
        }
    }

    public Envelope envelope(Long projectId, Long categoryId, String rule, String message) {
        if ((projectId == null) == (categoryId == null)) {
            throw new BusinessRuleViolationException(rule, message);
        }
        return projectId != null ? new Envelope(project(projectId), null) : new Envelope(null, category(categoryId));
    }

    public Project project(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + id));
    }

    public DonationCategory category(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Catégorie introuvable : " + id));
    }

    public Donor donorOrNull(Long id) {
        return id == null ? null
            : donorRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Personne introuvable : " + id));
    }

    public Activity activityOrNull(Long id) {
        return id == null ? null
            : activityRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Activité introuvable : " + id));
    }

    /** RG-15 : un canal désactivé ne peut plus être choisi pour un nouveau mouvement. */
    public PaymentChannel activeChannel(Long id) {
        PaymentChannel channel = paymentChannelRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Canal de paiement introuvable : " + id));
        if (!channel.isActive()) {
            throw new BusinessRuleViolationException("RG-15", "Le canal de paiement '" + channel.getLabel() + "' est désactivé.");
        }
        return channel;
    }

    public PaymentChannel activeChannelOrNull(Long id) {
        return id == null ? null : activeChannel(id);
    }

    public PaymentChannel otherChannel() {
        return paymentChannelRepository.findByCode(OTHER_CHANNEL_CODE)
            .orElseThrow(() -> new IllegalStateException("Canal de paiement " + OTHER_CHANNEL_CODE + " absent (migration 0012)."));
    }
}
