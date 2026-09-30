package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Consomme le jeton d'invitation (User.invitationToken) pour fixer le premier mot de passe. */
public record ActivateAccountRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères") String password
) {
}
