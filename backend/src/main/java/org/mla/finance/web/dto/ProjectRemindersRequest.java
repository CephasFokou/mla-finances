package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.ProjectReminderAudience;

import java.time.LocalDate;
import java.util.List;

/**
 * Bloc « Relances » du formulaire d'une collecte (cadrage Relances §6 bis). Pas de montant : la participation est libre.
 * Ouverture par défaut : date de début de la collecte, sinon le jour de l'activation des relances. Échéance par défaut :
 * date de fin de la collecte ; obligatoire si la collecte n'a pas de fin.
 */
public record ProjectRemindersRequest(
    boolean enabled,
    @NotNull ProjectReminderAudience audience,
    LocalDate openFrom,
    LocalDate dueDate,
    @Size(max = 120) String message,
    @Size(max = 300) List<@NotNull Long> memberIds
) {
}
