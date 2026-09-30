package org.mla.finance.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un point du graphique mensuel : month est normalisé au 1er du mois concerné. */
public record MonthlyAmount(LocalDate month, BigDecimal contributions, BigDecimal withdrawals) {
}
