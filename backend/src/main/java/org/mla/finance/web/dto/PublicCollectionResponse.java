package org.mla.finance.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vue publique d'une collecte pour la page de don (collectes ouvertes, à venir, et « ce que vos
 * dons ont permis ») : uniquement des montants agrégés, jamais de donateur ni de description
 * interne (une description peut nommer des personnes).
 *
 * @param state OPEN (en cours, on peut donner), UPCOMING (pas encore ouverte), CLOSED (clôturée)
 */
public record PublicCollectionResponse(
    Long id,
    String name,
    String state,
    BigDecimal collected,
    BigDecimal handedOver,
    long contributionsCount,
    LocalDate firstMovementDate,
    LocalDate lastMovementDate
) {
}
