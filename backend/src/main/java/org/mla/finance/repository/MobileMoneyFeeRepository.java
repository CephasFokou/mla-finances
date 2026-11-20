package org.mla.finance.repository;

import org.mla.finance.domain.MobileMoneyFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MobileMoneyFeeRepository extends JpaRepository<MobileMoneyFee, Long> {

    @Query("select f.ledgerMovementId from MobileMoneyFee f where f.ledgerMovementId in :ids")
    List<Long> findKnownMovementIds(@Param("ids") Collection<Long> ids);

    /** Σ des frais comptés depuis un instant (contrôle d'écart avec le solde wallet-ledger). */
    @Query("select coalesce(sum(f.amount), 0) from MobileMoneyFee f where f.occurredAt >= :since")
    BigDecimal sumSince(@Param("since") Instant since);
}
