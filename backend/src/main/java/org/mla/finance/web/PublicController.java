package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.service.LedgerReportsService;
import org.mla.finance.service.MonthlyContributionService;
import org.mla.finance.service.PublicDonationService;
import org.mla.finance.web.dto.LedgerReports;
import org.mla.finance.web.dto.PublicCollectionResponse;
import org.mla.finance.web.dto.PublicDonationRequest;
import org.mla.finance.web.dto.PublicDonationResponse;
import org.mla.finance.web.dto.PublicDonationStatusResponse;
import org.mla.finance.web.dto.PublicMonthlyContributionResponse;
import org.mla.finance.web.dto.PublicPaymentChannelResponse;
import org.mla.finance.web.dto.PublicProjectResponse;
import org.mla.finance.web.dto.PublicStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Endpoints du front-office public (page de don) — aucune authentification requise
 * (SecurityConfig exempte /api/v1/public/**). Ne jamais exposer ici de données individuelles (nom
 * de donateur, montant par donateur) : uniquement des projets actifs et des statistiques
 * agrégées. Seule exception : le nom usuel des membres, pour la contribution mensuelle. Les dons
 * restent anonymes pour tous sur le site (décision de Cephas, 19/11/2026).
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicDonationService publicDonationService;
    private final LedgerReportsService ledgerReportsService;
    private final MonthlyContributionService monthlyContributionService;

    @GetMapping("/projects")
    public List<PublicProjectResponse> projects() {
        return publicDonationService.activeProjects();
    }

    /** Canaux proposés au public (actifs sauf « Autre »), avec online=true pour ceux payés via PawaPay. */
    @GetMapping("/payment-channels")
    public List<PublicPaymentChannelResponse> paymentChannels() {
        return publicDonationService.paymentChannels();
    }

    /**
     * Collectes ouvertes, à venir et clôturées, avec leurs totaux agrégés, de la plus récente à la
     * plus ancienne (dernier mouvement). Jamais de détail par donateur.
     */
    @GetMapping("/collections")
    public List<PublicCollectionResponse> collections() {
        return ledgerReportsService.collections().stream()
            .filter(r -> r.status() != ProjectStatus.ARCHIVED)
            .sorted(Comparator.comparing(LedgerReports.CollectionRow::lastMovementDate,
                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
            .map(r -> new PublicCollectionResponse(
                r.project().id(),
                r.project().name(),
                switch (r.status()) {
                    case ACTIVE -> "OPEN";
                    case DRAFT -> "UPCOMING";
                    default -> "CLOSED";
                },
                r.collected(),
                r.used(),
                r.contributionsCount(),
                r.firstMovementDate(),
                r.lastMovementDate()))
            .toList();
    }

    /**
     * Contribution mensuelle des membres (caisse MLA) : membres actifs par leur nom usuel avec le
     * montant suggéré, et mois proposés. Seule exception à la règle « aucune donnée individuelle »,
     * décidée par Cephas : le nom usuel, jamais le téléphone, le nom complet ni le rôle.
     */
    @GetMapping("/monthly-contribution")
    public PublicMonthlyContributionResponse monthlyContribution() {
        return monthlyContributionService.publicOffer();
    }

    @GetMapping("/stats")
    public PublicStatsResponse stats() {
        return publicDonationService.stats();
    }

    @PostMapping("/donations")
    public ResponseEntity<PublicDonationResponse> donate(@Valid @RequestBody PublicDonationRequest request) {
        return ResponseEntity.ok(publicDonationService.submit(request));
    }

    /** Suivi d'un don par son jeton opaque (jamais par la référence, trop courte pour être non devinable). */
    @GetMapping("/donations/{trackingToken}")
    public PublicDonationStatusResponse donationStatus(@PathVariable String trackingToken) {
        return publicDonationService.status(trackingToken);
    }
}
