package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.ActivityStatus;

import java.time.LocalDate;

public record ActivityRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 60) String periodLabel,
    LocalDate eventDate,
    ActivityStatus status,
    @Size(max = 500) String comment
) {
}
