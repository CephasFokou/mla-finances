package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Création (code + libellé) ou modification (libellé, ordre ; le code est ignoré) d'un canal. */
public record PaymentChannelRequest(
    @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_]*$", message = "lettres majuscules, chiffres et _ uniquement") String code,
    @NotBlank @Size(max = 80) String label,
    Integer sortOrder
) {
}
