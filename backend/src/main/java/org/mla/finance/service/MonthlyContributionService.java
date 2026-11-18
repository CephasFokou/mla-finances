package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.MonthlyContributionSettings;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.MonthlyContributionSettingsRepository;
import org.mla.finance.web.dto.MonthlyContributionSettingsRequest;
import org.mla.finance.web.dto.MonthlyContributionSettingsResponse;
import org.mla.finance.web.dto.PublicMonthlyContributionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contribution mensuelle payée sur le site public : réglages (activé, montants suggérés par rôle),
 * offre publique (membres actifs et mois proposés) et contrôles d'une demande. Les mois se
 * calculent à l'heure de Douala : le serveur tourne en UTC, et un paiement fait le 1er à 00 h 30 à
 * Yaoundé doit déjà proposer le nouveau mois.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyContributionService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    /** Mois proposés au-delà du mois courant (paiement d'avance). */
    static final int MONTHS_AHEAD = 3;

    private final MonthlyContributionSettingsRepository settingsRepository;
    private final DonorRepository donorRepository;
    private final AuditLogService auditLogService;

    public MonthlyContributionSettings settings() {
        return settingsRepository.findById(MonthlyContributionSettings.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("Réglages de la contribution mensuelle absents (migration 0014)."));
    }

    public MonthlyContributionSettingsResponse getSettings() {
        return MonthlyContributionSettingsResponse.from(settings());
    }

    @Transactional
    public MonthlyContributionSettingsResponse updateSettings(MonthlyContributionSettingsRequest request) {
        MonthlyContributionSettings s = settings();
        s.setEnabled(request.enabled());
        s.setResponsableAmount(request.responsableAmount());
        s.setMemberAmount(request.memberAmount());
        MonthlyContributionSettings saved = settingsRepository.saveAndFlush(s);
        auditLogService.record("MONTHLY_CONTRIBUTION_SETTINGS", saved.getId(), "UPDATE",
            "Contribution mensuelle en ligne " + (saved.isEnabled() ? "activée" : "désactivée") + " ; montants suggérés : responsable "
                + saved.getResponsableAmount().toPlainString() + " FCFA, membre " + saved.getMemberAmount().toPlainString() + " FCFA");
        return MonthlyContributionSettingsResponse.from(saved);
    }

    /** Offre publique : vide si le parcours est désactivé (aucun nom de membre n'est alors exposé). */
    public PublicMonthlyContributionResponse publicOffer() {
        MonthlyContributionSettings s = settings();
        if (!s.isEnabled()) {
            return new PublicMonthlyContributionResponse(false, List.of(), List.of());
        }
        List<PublicMonthlyContributionResponse.Member> members = donorRepository.findAllByOrderByDisplayNameAscFullNameAsc().stream()
            .filter(MonthlyContributionService::isPayingMember)
            .sorted(Comparator.comparing(d -> d.getDisplayName().toLowerCase()))
            .map(d -> new PublicMonthlyContributionResponse.Member(d.getId(), d.getDisplayName(), s.suggestedAmount(d.getMemberRole())))
            .toList();
        return new PublicMonthlyContributionResponse(true, members, allowedMonths().stream().map(YearMonth::toString).toList());
    }

    /** De janvier de l'année en cours au mois courant + 3. */
    public List<YearMonth> allowedMonths() {
        YearMonth current = YearMonth.now(ZONE);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.of(current.getYear(), 1); !m.isAfter(current.plusMonths(MONTHS_AHEAD)); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    /** Membre actif (RESPONSABLE ou MEMBRE) ayant un nom usuel : seul le nom usuel est montré au public. */
    static boolean isPayingMember(Donor d) {
        return d.isMember() && d.getMemberStatus() == MemberStatus.ACTIVE
            && d.getDisplayName() != null && !d.getDisplayName().isBlank();
    }

    /** Membre qui paie : doit exister et être un membre actif proposé au public (sinon 422). */
    public Donor payingMember(Long donorId) {
        return donorRepository.findById(donorId)
            .filter(MonthlyContributionService::isPayingMember)
            .orElseThrow(() -> new BusinessRuleViolationException("DON-10",
                "Ce membre ne peut pas payer de contribution mensuelle en ligne (membre inconnu ou inactif)."));
    }

    /**
     * Mois demandés (« YYYY-MM ») : non vides et sans doublon (400), dans la plage proposée (422).
     * Renvoie le 1er de chaque mois, trié.
     */
    public List<LocalDate> requestedMonths(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidRequestException("months", "Choisissez au moins un mois.");
        }
        Set<YearMonth> seen = new HashSet<>();
        for (String value : raw) {
            YearMonth month;
            try {
                month = YearMonth.parse(value);
            } catch (DateTimeParseException | NullPointerException e) {
                throw new InvalidRequestException("months", "Mois invalide : " + value + " (format attendu : AAAA-MM).");
            }
            if (!seen.add(month)) {
                throw new InvalidRequestException("months", "Le mois " + value + " est demandé deux fois.");
            }
        }
        List<YearMonth> allowed = allowedMonths();
        for (YearMonth month : seen) {
            if (!allowed.contains(month)) {
                throw new BusinessRuleViolationException("DON-11", "Le mois " + month + " ne peut pas être payé en ligne : "
                    + "mois proposés de " + allowed.get(0) + " à " + allowed.get(allowed.size() - 1) + ".");
            }
        }
        return seen.stream().sorted().map(m -> m.atDay(1)).toList();
    }
}
