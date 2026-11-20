package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Réglages des relances (cadrage Relances §8) : une seule ligne (id = 1), créée par la migration 0018 et
 * modifiable dans Administration. L'échéance de la contribution mensuelle est toujours le dernier jour du mois.
 */
@Entity
@Table(name = "reminder_settings")
@Getter
@Setter
@NoArgsConstructor
public class ReminderSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Premier jour du mois où la contribution mensuelle peut être relancée. */
    @Column(name = "open_from_day", nullable = false)
    private int openFromDay = 10;

    @Column(name = "cooldown_no_response_days", nullable = false)
    private int cooldownNoResponseDays = 3;

    @Column(name = "cooldown_ack_days", nullable = false)
    private int cooldownAckDays = 5;

    @Column(name = "cooldown_not_found_hours", nullable = false)
    private int cooldownNotFoundHours = 48;

    @Column(name = "max_per_month", nullable = false)
    private int maxPerMonth = 3;

    @Column(name = "send_window_start", nullable = false)
    private LocalTime sendWindowStart = LocalTime.of(8, 0);

    @Column(name = "send_window_end", nullable = false)
    private LocalTime sendWindowEnd = LocalTime.of(20, 0);

    @Column(name = "auto_reminder_on_promise", nullable = false)
    private boolean autoReminderOnPromise = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
