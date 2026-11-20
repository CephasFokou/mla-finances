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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dossier de relance (cadrage Relances §3) : un membre, un motif, un mois (ou une collecte). Regroupe les relances
 * successives (au plus {@link ReminderSettings#getMaxPerMonth()}) et, à partir du lot 2, les réponses du membre.
 * Jamais supprimé (RL-10) : un dossier se clôt en REGLE ou SANS_SUITE.
 */
@Entity
@Table(name = "reminder_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReminderReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "period_month")
    private LocalDate periodMonth;

    /** Clé d'unicité avec (donor, reason) : « M:2026-11-01 » ou « P:12 ». */
    @Column(name = "case_key", nullable = false, length = 40)
    private String caseKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReminderCaseStatus status;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "next_allowed_at")
    private Instant nextAllowedAt;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "last_response", length = 20)
    private String lastResponse;

    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_reason", length = 300)
    private String closedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    /** Contribution qui a clos le dossier en REGLE (RL-08). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static String monthKey(LocalDate periodMonth) {
        return "M:" + periodMonth;
    }

    public boolean isClosed() {
        return status.isClosed();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
