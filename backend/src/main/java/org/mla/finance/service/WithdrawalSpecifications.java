package org.mla.finance.service;

import jakarta.persistence.criteria.Predicate;
import org.mla.finance.domain.Withdrawal;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class WithdrawalSpecifications {

    private WithdrawalSpecifications() {
    }

    static Specification<Withdrawal> matching(WithdrawalFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.projectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), f.projectId()));
            }
            if (f.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.dateFrom() != null) {
                predicates.add(cb.or(
                    cb.greaterThanOrEqualTo(root.get("operationDate"), f.dateFrom()),
                    cb.and(cb.isNull(root.get("operationDate")),
                        cb.greaterThanOrEqualTo(root.get("requestedAt"), toStartOfDay(f.dateFrom())))));
            }
            if (f.dateTo() != null) {
                predicates.add(cb.or(
                    cb.lessThanOrEqualTo(root.get("operationDate"), f.dateTo()),
                    cb.and(cb.isNull(root.get("operationDate")),
                        cb.lessThan(root.get("requestedAt"), toStartOfDay(f.dateTo().plusDays(1))))));
            }
            if (f.nature() != null) {
                predicates.add(cb.equal(root.get("nature"), f.nature()));
            }
            if (f.activityId() != null) {
                predicates.add(cb.equal(root.get("activity").get("id"), f.activityId()));
            }
            if (f.personId() != null) {
                predicates.add(cb.equal(root.get("person").get("id"), f.personId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static java.time.Instant toStartOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
