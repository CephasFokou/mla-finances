package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Modification limitée aux champs descriptifs et à la cible : ni le statut (actions dédiées),
 * ni le solde d'ouverture, ni la série récurrente ne se modifient ici. */
public record ProjectUpdateRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 1000) String description,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
    LocalDate startDate,
    LocalDate endDate
) {
}
