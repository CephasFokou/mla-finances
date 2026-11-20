package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReminderBulkRequest(
    @NotEmpty @Size(max = 300) List<@NotNull Long> donorIds,
    @NotNull @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mois attendu au format AAAA-MM") String month
) {
}
