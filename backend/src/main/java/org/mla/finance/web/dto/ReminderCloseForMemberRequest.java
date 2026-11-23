package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Clôture d'un membre pour un mois, même s'il n'a encore jamais été relancé (dossier créé au passage). */
public record ReminderCloseForMemberRequest(
    @NotNull Long donorId,
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mois attendu au format AAAA-MM") String month,
    Long projectId,
    @NotBlank @Size(max = 300) String reason
) {
    @AssertTrue(message = "Indiquez le mois ou la collecte.")
    public boolean isMotiveGiven() {
        return projectId != null || month != null;
    }
}
