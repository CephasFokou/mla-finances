package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RG-10 : transfert interne entre deux enveloppes (caisse ↔ collecte, collecte ↔ collecte). Source
 * et destination : un projet XOR une catégorie chacune.
 */
public record TransferRequest(
    Long fromProjectId,
    Long fromCategoryId,
    Long toProjectId,
    Long toCategoryId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotNull @PastOrPresent LocalDate operationDate,
    @NotBlank @Size(max = 500) String reason,
    Long activityId
) {
}
