package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** RG-19 : crée une nouvelle occurrence d'un événement récurrent (ex. Comfrat 2027), qui reprend
 * le recurringSeriesId du projet source. */
public record ProjectOccurrenceRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 1000) String description,
    @NotNull @DecimalMin(value = "0") @Digits(integer = 12, fraction = 2) BigDecimal openingBalance,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
    LocalDate startDate,
    LocalDate endDate
) {
}
