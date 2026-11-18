package org.mla.finance.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Offre publique de contribution mensuelle (GET /public/monthly-contribution). Les membres ne sont
 * exposés que par leur nom usuel, avec le montant suggéré qui découle de leur rôle : ni téléphone,
 * ni nom complet, ni rôle (décision de Cephas, 18/11/2026). Désactivé : listes vides.
 */
public record PublicMonthlyContributionResponse(boolean enabled, List<Member> members, List<String> months) {

    public record Member(Long id, String displayName, BigDecimal suggestedAmount) {
    }
}
