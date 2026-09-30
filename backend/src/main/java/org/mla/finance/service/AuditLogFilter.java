package org.mla.finance.service;

import java.time.LocalDate;

/** Filtres du journal d'audit (GET /api/v1/reports/audit). Tous les champs sont optionnels. */
public record AuditLogFilter(
    String entityType,
    Long entityId,
    String action,
    LocalDate dateFrom,
    LocalDate dateTo
) {
}
