package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Frais Mobile Money (ligne FEE du grand livre wallet-ledger) déjà compté dans MLA Finance : la
 * dépense « Frais Mobile Money » qui l'inclut, et à quelle opération il a été rattaché. L'unicité de
 * ledgerMovementId garantit qu'un frais n'est jamais compté deux fois (voir MobileMoneyFeeSyncService).
 */
@Entity
@Table(name = "mobile_money_fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MobileMoneyFee {

    public enum OperationKind { DEPOSIT, PAYOUT, NONE }

    /** Comment l'enveloppe a été trouvée. */
    public enum Attribution { OPERATION, CLIENT_REFERENCE, MAIN_FUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_movement_id", nullable = false, unique = true)
    private Long ledgerMovementId;

    /** Montant positif. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "operation_kind", nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private OperationKind operationKind;

    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "client_reference", length = 100)
    private String clientReference;

    @Column(nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Attribution attribution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_id", nullable = false)
    private Withdrawal withdrawal;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
