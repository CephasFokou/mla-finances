package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un projet est toujours créé en statut DRAFT (voir ProjectService) : le statut n'est jamais
 * choisi à la création, il évolue ensuite via les actions dédiées (activer/clôturer/archiver).
 * Le solde d'ouverture, comme le montant d'une contribution (RG-17), n'est plus modifiable une
 * fois le projet créé — décision à confirmer avec Cephas si un besoin de correction se présente.
 */
public record ProjectCreateRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 1000) String description,
    @NotNull @DecimalMin(value = "0") @Digits(integer = 12, fraction = 2) BigDecimal openingBalance,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
    @Size(max = 60) String recurringSeriesId,
    LocalDate startDate,
    LocalDate endDate
) {
}
