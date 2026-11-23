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

import java.time.Instant;
import java.time.LocalDate;

/** Réponse d'un membre à une relance, telle que reçue (RL-10 : historique complet du dossier). */
@Entity
@Table(name = "reminder_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private ReminderCase reminderCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 20)
    private ReminderResponseType type;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "raw_text", length = 500)
    private String rawText;

    /** wamid du message WhatsApp (idempotence) ; null pour une réponse par la page des emails. */
    @Column(name = "provider_message_id", length = 120, unique = true)
    private String providerMessageId;

    @Column(length = 300)
    private String outcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
