package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.service.PublicDonationService;
import org.mla.finance.service.ReferenceDataService;
import org.mla.finance.web.dto.PaymentChannelRef;
import org.mla.finance.web.dto.PublicDonationRequest;
import org.mla.finance.web.dto.PublicDonationResponse;
import org.mla.finance.web.dto.PublicProjectResponse;
import org.mla.finance.web.dto.PublicStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints du front-office public (page de don) — aucune authentification requise
 * (SecurityConfig exempte /api/v1/public/**). Ne jamais exposer ici de données individuelles (nom
 * de donateur, montant par donateur) : uniquement des projets actifs et des statistiques
 * agrégées.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicDonationService publicDonationService;
    private final ReferenceDataService referenceDataService;

    @GetMapping("/projects")
    public List<PublicProjectResponse> projects() {
        return publicDonationService.activeProjects();
    }

    @GetMapping("/payment-channels")
    public List<PaymentChannelRef> paymentChannels() {
        return referenceDataService.paymentChannels();
    }

    @GetMapping("/stats")
    public PublicStatsResponse stats() {
        return publicDonationService.stats();
    }

    @PostMapping("/donations")
    public ResponseEntity<PublicDonationResponse> donate(@Valid @RequestBody PublicDonationRequest request) {
        return ResponseEntity.ok(publicDonationService.submit(request));
    }
}
