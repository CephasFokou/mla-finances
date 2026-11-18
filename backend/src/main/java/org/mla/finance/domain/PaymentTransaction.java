package org.mla.finance.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Une intention de don reçue via le front-office public, avant qu'elle ne devienne une
 * Contribution réelle. Décorrélée de Contribution : tant qu'un PaymentProvider ne confirme pas
 * effectivement le paiement, aucune Contribution n'est créée — un don en attente ne doit jamais
 * gonfler un solde ou un rapport avant que l'argent ait réellement été reçu.
 * <p>
 * Pour l'instant, la seule implémentation de PaymentProvider (ManualPaymentProvider) ne fait
 * aucun appel externe : chaque don public reste PENDING jusqu'à ce qu'un membre du staff le
 * confirme manuellement (après vérification du paiement réel — Mobile Money, espèces au
 * secrétariat, etc.), ce qui crée alors la Contribution.
 * <p>
 * En mode en ligne (PawaPayProvider, canaux Orange Money / MTN MoMo), le don passe en PROCESSING
 * dès que PawaPay accepte la demande, puis est résolu (CONFIRMED → Contribution, ou FAILED) par la
 * réconciliation (callback PawaPay ou job de polling), jamais par une action manuelle. Clés
 * d'idempotence (RG-20) : {@code depositId}, UUID généré ici et envoyé à PawaPay (PawaPay ignore
 * tout doublon), et le verrou de ligne pris par PaymentTransactionService.applyProviderOutcome
 * avant tout changement d'état — un statut final n'évolue plus, quel que soit le nombre de
 * callbacks ou de relectures reçus.
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

    /** Première contribution créée à la confirmation (une contribution mensuelle en crée une par mois). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentTransactionKind kind = PaymentTransactionKind.DONATION;

    /** Membre qui paie sa contribution mensuelle (MONTHLY_CONTRIBUTION uniquement). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private Donor donor;

    /** Montant par mois (MONTHLY_CONTRIBUTION) ; amount = amountPerMonth × nombre de mois. */
    @Column(name = "amount_per_month", precision = 14, scale = 2)
    private BigDecimal amountPerMonth;

    /** Mois couverts (1er du mois), triés (MONTHLY_CONTRIBUTION uniquement). */
    @ElementCollection
    @CollectionTable(name = "payment_transaction_months", joinColumns = @JoinColumn(name = "payment_transaction_id"))
    @Column(name = "period_month", nullable = false)
    @jakarta.persistence.OrderBy
    @Builder.Default
    private List<LocalDate> coveredMonths = new ArrayList<>();

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

    /** UUID v4 envoyé à PawaPay comme depositId (null en mode manuel). Unique : clé d'idempotence RG-20. */
    @Column(name = "deposit_id", length = 36, unique = true)
    private String depositId;

    /**
     * Jeton opaque (UUID) remis au donateur pour suivre son don (GET /public/donations/{token}).
     * Distinct de la référence lisible MLA-xxxxxx, trop courte pour être non devinable : on ne
     * doit pas pouvoir énumérer les dons des autres.
     */
    @Column(name = "tracking_token", length = 36, unique = true)
    private String trackingToken;

    /** MSISDN du payeur Mobile Money (ex. 2376xxxxxxxx), uniquement en mode en ligne. */
    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    /** Dernier statut brut rapporté par le prestataire (ACCEPTED, PROCESSING, COMPLETED, FAILED...). */
    @Column(name = "provider_status", length = 30)
    private String providerStatus;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    /** Prochaine relecture planifiée du statut auprès du prestataire ; null = plus de relecture automatique. */
    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    @Column(name = "check_count", nullable = false)
    @Builder.Default
    private int checkCount = 0;

    /**
     * Signalé au back-office : statut non résolu après le délai de réconciliation, ou anomalie
     * (ex. paiement complété chez PawaPay après une annulation locale). N'est jamais un statut.
     */
    @Column(name = "needs_review", nullable = false)
    @Builder.Default
    private boolean needsReview = false;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isMonthlyContribution() {
        return kind == PaymentTransactionKind.MONTHLY_CONTRIBUTION;
    }

    public boolean isOnline() {
        return depositId != null;
    }
}
