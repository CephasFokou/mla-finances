package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un donateur identifié. RG-14 : un don peut rester anonyme (voir Contribution.anonymous) même
 * lorsqu'un donateur est techniquement rattaché en base pour le suivi interne.
 */
@Entity
@Table(name = "donors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", length = 180)
    private String fullName;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    /** Nom usuel (« Bravo », « Maman Jeanne »), unique : c'est le nom utilisé dans le journal. */
    @Column(name = "display_name", length = 80, unique = true)
    private String displayName;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    /** Null pour un tiers (donateur externe), RESPONSABLE ou MEMBRE pour un membre du MLA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", length = 20)
    private MemberRole memberRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", length = 20)
    private MemberStatus memberStatus;

    @Column(length = 500)
    private String remark;

    public boolean isMember() {
        return memberRole != null;
    }

    /** Nom affiché partout : nom usuel s'il existe, sinon nom complet. */
    public String getLabel() {
        return displayName != null && !displayName.isBlank() ? displayName : fullName;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
