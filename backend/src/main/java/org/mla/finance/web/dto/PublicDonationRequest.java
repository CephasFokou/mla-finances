package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PublicDonationRequest(
    @NotNull Long projectId,
    @NotNull Long paymentChannelId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
    boolean anonymous,
    @Size(max = 160) String donorName,
    @Size(max = 160) String donorContact
) {
}
