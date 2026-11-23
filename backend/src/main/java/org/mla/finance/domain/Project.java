package org.mla.finance.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
import java.util.HashSet;
import java.util.Set;

/**
 * Un projet/enveloppe (ex. "Toiture du Temple Central", ou une occurrence de Comfrat).
 * RG-07 : le solde n'est jamais stocké, il se calcule à partir de openingBalance + contributions
 * (voir la future couche service). RG-19 : un événement récurrent (Comfrat) est modélisé en
 * occurrences distinctes partageant le même recurringSeriesId.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "target_amount", precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "recurring_series_id", length = 60)
    private String recurringSeriesId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Relances de la collecte (cadrage Relances §6 bis) : désactivées par défaut. */
    @Column(name = "reminders_enabled", nullable = false)
    @Builder.Default
    private boolean remindersEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_audience", nullable = false, length = 20)
    @Builder.Default
    private ProjectReminderAudience reminderAudience = ProjectReminderAudience.ALL;

    /** Ouverture des relances ; toujours renseignée quand les relances sont activées. */
    @Column(name = "reminder_open_from")
    private LocalDate reminderOpenFrom;

    /** Échéance des relances ; toujours renseignée quand les relances sont activées. */
    @Column(name = "reminder_due_date")
    private LocalDate reminderDueDate;

    /** Message court de l'admin repris dans la relance (ex. « cérémonie le 6 décembre »). */
    @Column(name = "reminder_message", length = 120)
    private String reminderMessage;

    /** Membres ciblés quand {@link #reminderAudience} vaut SELECTION. */
    @ElementCollection
    @CollectionTable(name = "project_reminder_members", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "donor_id", nullable = false)
    @Builder.Default
    private Set<Long> reminderMemberIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
