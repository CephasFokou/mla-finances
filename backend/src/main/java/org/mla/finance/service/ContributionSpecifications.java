package org.mla.finance.service;

import jakarta.persistence.criteria.Predicate;
import org.mla.finance.domain.Contribution;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class ContributionSpecifications {

    private ContributionSpecifications() {
    }

    static Specification<Contribution> matching(ContributionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.projectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), f.projectId()));
            }
            if (f.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.donorId() != null) {
                predicates.add(cb.equal(root.get("donor").get("id"), f.donorId()));
            }
            if (f.paymentChannelId() != null) {
                predicates.add(cb.equal(root.get("paymentChannel").get("id"), f.paymentChannelId()));
            }
            if (f.movementType() != null) {
                predicates.add(cb.equal(root.get("movementType"), f.movementType()));
            }
            if (f.anonymous() != null) {
                predicates.add(cb.equal(root.get("anonymous"), f.anonymous()));
            }
            if (f.periodMonth() != null) {
                predicates.add(cb.equal(root.get("periodMonth"), f.periodMonth().atDay(1)));
            }
            if (f.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), f.dateFrom()));
            }
            if (f.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), f.dateTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
