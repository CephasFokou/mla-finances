package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Trace d'audit append-only (RG-17 "audit systématique") : chaque mutation métier significative
 * (création/annulation d'une contribution, cycle de vie d'un retrait, cycle de vie d'un
 * projet/catégorie) enregistre ici une ligne, écrite par {@code AuditLogService.record(...)} dans
 * la même transaction que la mutation qu'elle documente. Aucune mise à jour ni suppression n'est
 * prévue une fois la ligne écrite.
 *
 * entityType/entityId identifient l'objet métier concerné (ex. "WITHDRAWAL"/42), action est un
 * verbe court (CREATE, APPROVE, REJECT, CANCEL, PROCESS, ACTIVATE, COMPLETE, ARCHIVE, DEACTIVATE),
 * summary est la phrase déjà formée à afficher dans le journal (évite de reconstruire un libellé
 * lisible côté frontend à partir des codes).
 *
 * actor reste toujours null tant que l'authentification réelle n'est pas branchée (même
 * convention que Withdrawal.requestedBy / Contribution.createdBy) : le champ existe pour ne pas
 * nécessiter de migration supplémentaire le jour où un utilisateur courant sera disponible.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 40)
    private String action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
