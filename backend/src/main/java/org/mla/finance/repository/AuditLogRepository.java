package org.mla.finance.repository;

import org.mla.finance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // Évite le N+1 lors de la conversion en réponse (actor est LAZY).
    @Override
    @EntityGraph(attributePaths = {"actor"})
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
}
