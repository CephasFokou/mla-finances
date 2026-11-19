package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Une demande de retrait sur une enveloppe (Project XOR DonationCategory, même principe que
 * Contribution — RG-02 par analogie). Workflow retrait : demande (PENDING), validation
 * (APPROVED/REJECTED par un rôle distinct du demandeur), puis décaissement effectif (PROCESSED).
 * Tant qu'une demande n'est pas PROCESSED, son montant est "engagé" (dépense engagée) : il réduit
 * le solde prévisionnel de l'enveloppe sans toucher au solde réel (voir BalanceService).
 */
@Entity
@Table(name = "withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DonationCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private User decidedBy;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MovementNature nature = MovementNature.EXPENSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    /** Membre ou tiers qui a effectué la dépense ou reçu l'argent (colonne « Membre / Tiers »). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Donor person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_channel_id")
    private PaymentChannel paymentChannel;

    /** Date réelle de la sortie d'argent, renseignée au décaissement (PROCESSED). */
    @Column(name = "operation_date")
    private LocalDate operationDate;

    @Column(length = 500)
    private String remark;

    @Column(name = "transfer_group", length = 40)
    private String transferGroup;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(length = 60, unique = true)
    private String reference;

    // ------------------------------------------------------------------ décaissement Mobile Money
    // Dernier payout wallet-ledger de ce retrait (voir PayoutStatus et WithdrawalPayoutService).

    /** Notre UUID, généré et enregistré AVANT l'appel (idempotence : wallet-ledger ignore un doublon). */
    @Column(name = "payout_id", length = 36, unique = true)
    private String payoutId;

    /** MSISDN complet (237...) d'un destinataire de la liste fermée app.payment.payout-recipients. */
    @Column(name = "payout_phone", length = 20)
    private String payoutPhone;

    /** Code opérateur PawaPay (ORANGE_CMR, MTN_MOMO_CMR). */
    @Column(name = "payout_provider", length = 30)
    private String payoutProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status", length = 20)
    private PayoutStatus payoutStatus;

    @Column(name = "payout_failure_code", length = 60)
    private String payoutFailureCode;

    @Column(name = "payout_requested_at")
    private Instant payoutRequestedAt;

    @Column(name = "payout_completed_at")
    private Instant payoutCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_requested_by")
    private User payoutRequestedBy;

    /** Prochaine relecture due par le job de polling (null : rien à relire). */
    @Column(name = "payout_next_check_at")
    private Instant payoutNextCheckAt;

    public boolean hasActivePayout() {
        return payoutStatus != null && payoutStatus.isActive();
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (requestedAt == null) {
            requestedAt = now;
        }
    }
}
