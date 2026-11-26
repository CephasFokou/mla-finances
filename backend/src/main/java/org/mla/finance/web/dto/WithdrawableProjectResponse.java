package org.mla.finance.web.dto;

import org.mla.finance.domain.ProjectStatus;

import java.math.BigDecimal;

/**
 * Collecte proposée dans le formulaire de sortie : ouverte (ACTIVE), ou clôturée (COMPLETED) avec un
 * solde prévisionnel encore positif. remainingBalance n'est renseigné que pour une collecte clôturée :
 * c'est le plafond d'une sortie sur cette enveloppe.
 */
public record WithdrawableProjectResponse(
    Long id,
    String name,
    ProjectStatus status,
    BigDecimal remainingBalance
) {
}
