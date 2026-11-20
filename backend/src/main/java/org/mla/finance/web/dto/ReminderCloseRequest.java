package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Clôture manuelle d'un dossier (ex. membre dispensé ce mois) : passe en « Sans suite ». */
public record ReminderCloseRequest(@NotBlank @Size(max = 300) String reason) {
}
