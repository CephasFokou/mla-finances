package org.mla.finance.web.dto;

import java.math.BigDecimal;

/** Vue publique et allégée d'un projet actif : uniquement ce qu'un donateur a besoin de voir. */
public record PublicProjectResponse(
    Long id,
    String name,
    String description,
    BigDecimal targetAmount,
    BigDecimal currentBalance,
    Integer progressPercent
) {
}
