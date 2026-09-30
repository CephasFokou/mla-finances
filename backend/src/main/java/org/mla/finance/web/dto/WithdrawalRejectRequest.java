package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRejectRequest(@NotBlank @Size(max = 500) String reason) {
}
