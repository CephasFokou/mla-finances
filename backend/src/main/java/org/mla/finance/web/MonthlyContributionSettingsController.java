package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.service.MonthlyContributionService;
import org.mla.finance.web.dto.MonthlyContributionSettingsRequest;
import org.mla.finance.web.dto.MonthlyContributionSettingsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du parcours « Contribution mensuelle » du site public : activé ou non, montants
 * suggérés par rôle. Lecture pour tout utilisateur connecté ; modification réservée à
 * MANAGE_USERS, comme les autres réglages de l'onglet Administration (canaux de paiement).
 */
@RestController
@RequestMapping("/api/v1/settings/monthly-contribution")
@RequiredArgsConstructor
public class MonthlyContributionSettingsController {

    private final MonthlyContributionService monthlyContributionService;

    @GetMapping
    public MonthlyContributionSettingsResponse get() {
        return monthlyContributionService.getSettings();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public MonthlyContributionSettingsResponse update(@Valid @RequestBody MonthlyContributionSettingsRequest request) {
        return monthlyContributionService.updateSettings(request);
    }
}
