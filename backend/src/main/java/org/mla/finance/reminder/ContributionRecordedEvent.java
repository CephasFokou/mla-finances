package org.mla.finance.reminder;

import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.MovementNature;

import java.time.LocalDate;

/**
 * Publié à chaque contribution enregistrée (saisie back-office, confirmation d'un don en ligne ou manuel). Traité
 * après validation de la transaction par ReminderService : clôt les dossiers de relance réglés (RL-08), sans balayage.
 */
public record ContributionRecordedEvent(Long contributionId, Long donorId, MovementNature nature, LocalDate periodMonth,
                                        Long projectId) {

    public static ContributionRecordedEvent of(Contribution c) {
        return new ContributionRecordedEvent(
            c.getId(),
            c.getDonor() == null ? null : c.getDonor().getId(),
            c.getNature(),
            c.getPeriodMonth(),
            c.getProject() == null ? null : c.getProject().getId()
        );
    }
}
