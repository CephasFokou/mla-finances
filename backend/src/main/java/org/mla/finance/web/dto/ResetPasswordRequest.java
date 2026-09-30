package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Consomme le jeton de réinitialisation (User.passwordResetToken) pour fixer un nouveau mot de passe. */
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères") String password
) {
}
