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

/**
 * Une intention de don reçue via le front-office public, avant qu'elle ne devienne une
 * Contribution réelle. Décorrélée de Contribution : tant qu'un PaymentProvider ne confirme pas
 * effectivement le paiement, aucune Contribution n'est créée — un don en attente ne doit jamais
 * gonfler un solde ou un rapport avant que l'argent ait réellement été reçu.
 * <p>
 * Pour l'instant, la seule implémentation de PaymentProvider (ManualPaymentProvider) ne fait
 * aucun appel externe : chaque don public reste PENDING jusqu'à ce qu'un membre du staff le
 * confirme manuellement (après vérification du paiement réel — Mobile Money, espèces au
 * secrétariat, etc.), ce qui crée alors la Contribution. Le jour où PawaPay sera branché, une
 * nouvelle implémentation de PaymentProvider fera cette confirmation automatiquement (webhook,
 * RG-20 idempotence) sans changer ce modèle.
 */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DonationCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_channel_id", nullable = false)
    private PaymentChannel paymentChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id")
    private User confirmedBy;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "donor_name", length = 160)
    private String donorName;

    @Column(name = "donor_contact", length = 160)
    private String donorContact;

    @Column(nullable = false)
    @Builder.Default
    private boolean anonymous = false;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "MANUAL";

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING;

    @Column(length = 60, unique = true, nullable = false)
    private String reference;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
