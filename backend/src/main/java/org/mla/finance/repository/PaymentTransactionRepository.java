package org.mla.finance.repository;

import jakarta.persistence.LockModeType;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Page<PaymentTransaction> findByStatus(PaymentTransactionStatus status, Pageable pageable);

    Optional<PaymentTransaction> findByDepositId(String depositId);

    Optional<PaymentTransaction> findByReference(String reference);

    /** Σ des dons Mobile Money confirmés passés par wallet-ledger depuis un instant (contrôle d'écart). */
    @Query("select coalesce(sum(t.amount), 0) from PaymentTransaction t where t.provider = :provider "
        + "and t.status = org.mla.finance.domain.PaymentTransactionStatus.CONFIRMED and t.depositId is not null "
        + "and t.confirmedAt >= :since")
    java.math.BigDecimal sumConfirmedSince(@Param("provider") String provider, @Param("since") java.time.Instant since);

    Optional<PaymentTransaction> findByTrackingToken(String trackingToken);

    /**
     * Verrou pessimiste (SELECT ... FOR UPDATE) sur la ligne avant tout changement d'état issu du
     * prestataire : un callback PawaPay et le job de polling qui arrivent en même temps se
     * sérialisent au lieu de créer deux Contributions (RG-20).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    /** Dons en ligne dont la relecture de statut est due. */
    List<PaymentTransaction> findByStatusAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(
        PaymentTransactionStatus status, Instant now, Pageable pageable);

    /** Anti-abus : demandes de paiement récentes vers un même numéro, tous statuts confondus. */
    long countByPayerPhoneAndCreatedAtAfter(String payerPhone, Instant after);
}
