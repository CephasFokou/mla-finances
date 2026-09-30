package org.mla.finance.web.dto;

import org.mla.finance.domain.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
    Long id,
    String entityType,
    Long entityId,
    String action,
    String actorName,
    String summary,
    Instant occurredAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
            log.getId(),
            log.getEntityType(),
            log.getEntityId(),
            log.getAction(),
            log.getActor() != null ? log.getActor().getFullName() : null,
            log.getSummary(),
            log.getOccurredAt()
        );
    }
}
