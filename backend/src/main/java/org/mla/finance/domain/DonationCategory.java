package org.mla.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catégorie de don générale (ex. Caisse générale, Comfrat), alternative au rattachement à un Project (RG-02, XOR). */
@Entity
@Table(name = "donation_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Caisse principale (Caisse MLA) : ses mouvements ne se mélangent jamais avec ceux des collectes. */
    @Column(name = "main_fund", nullable = false)
    @Builder.Default
    private boolean mainFund = false;
}
