package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRolesUpdateRequest(@NotNull @NotEmpty List<Long> roleIds) {
}
