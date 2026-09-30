package org.mla.finance.service;

import jakarta.persistence.criteria.Predicate;
import org.mla.finance.domain.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Même principe que WithdrawalSpecifications/ContributionSpecifications : un Predicate par filtre renseigné. */
final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    static Specification<AuditLog> matching(AuditLogFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.entityType() != null && !f.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), f.entityType()));
            }
            if (f.entityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), f.entityId()));
            }
            if (f.action() != null && !f.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), f.action()));
            }
            if (f.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), toStartOfDay(f.dateFrom())));
            }
            if (f.dateTo() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), toStartOfDay(f.dateTo().plusDays(1))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Instant toStartOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
