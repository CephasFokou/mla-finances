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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Réglages du parcours « Contribution mensuelle » du site public : une seule ligne (id = 1), créée
 * par la migration 0014. Les montants suggérés préremplissent le montant par mois selon le rôle du
 * membre ; le membre peut toujours le modifier.
 */
@Entity
@Table(name = "monthly_contribution_settings")
@Getter
@Setter
@NoArgsConstructor
public class MonthlyContributionSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "responsable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal responsableAmount = BigDecimal.valueOf(5000);

    @Column(name = "member_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal memberAmount = BigDecimal.valueOf(2000);

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public BigDecimal suggestedAmount(MemberRole role) {
        return role == MemberRole.RESPONSABLE ? responsableAmount : memberAmount;
    }
}
