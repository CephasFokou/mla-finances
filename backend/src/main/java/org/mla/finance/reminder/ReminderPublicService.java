package org.mla.finance.reminder;

import jakarta.persistence.EntityNotFoundException;
import org.mla.finance.domain.AccessToken;
import org.mla.finance.domain.AccessTokenPurpose;
import org.mla.finance.domain.MonthlyContributionSettings;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.domain.ReminderResponseType;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.repository.ReminderCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Côté site de don : page de réponse d'un email de relance (/relance/{jeton}) et bouton « Contribuer maintenant »
 * (/c/{jeton}). Un jeton inconnu, expiré ou mal formé donne toujours la même réponse (404), sans détail. Rien n'est
 * déclenché par une simple ouverture du lien (les messageries ouvrent parfois les liens pour les analyser) : la page
 * attend un clic du membre.
 */
@Service
public class ReminderPublicService {

    private static final String INVALID = "Ce lien n'est plus valable.";

    private final AccessTokenService tokenService;
    private final ReminderCaseRepository caseRepository;
    private final ReminderResponseService responseService;
    private final ReminderService reminderService;

    public ReminderPublicService(AccessTokenService tokenService, ReminderCaseRepository caseRepository,
                                 ReminderResponseService responseService, ReminderService reminderService) {
        this.tokenService = tokenService;
        this.caseRepository = caseRepository;
        this.responseService = responseService;
        this.reminderService = reminderService;
    }

    public record DateOption(LocalDate date, String label) {
    }

    /** Page de réponse : prénom usuel, mois, état du dossier, dates proposées si « Bien reçu ». */
    public record ActionView(String name, String reason, String month, String monthLabel, String projectName,
                             LocalDate dueDate, String status, boolean settled, boolean open, List<DateOption> dateOptions) {
    }

    public record ActionResult(String message, String status, List<DateOption> dateOptions) {
    }

    /**
     * Lien « Contribuer maintenant » : le membre et le mois à présélectionner sur le site de don ; pour une collecte
     * (reason PROJECT), la collecte à ouvrir — le jeton accompagne alors le don pour le rattacher au membre.
     */
    public record ContributionLink(String reason, Long donorId, String displayName, BigDecimal suggestedAmount,
                                   List<String> months, Long projectId, String projectName) {
    }

    @Transactional(readOnly = true)
    public ActionView view(String token) {
        ReminderCase rc = caseFor(token, AccessTokenPurpose.REMINDER_ACTION);
        ReminderMotive motive = ReminderMotive.of(rc);
        boolean settled = rc.getStatus() == ReminderCaseStatus.REGLE
            || reminderService.facts(rc.getDonor(), motive, rc).paid();
        boolean open = !settled && rc.getStatus() != ReminderCaseStatus.SANS_SUITE;
        return new ActionView(rc.getDonor().getLabel(), rc.getReason().name(),
            motive.isProject() ? null : motive.month().toString(), motive.isProject() ? null : motive.label(),
            motive.isProject() ? motive.project().getName() : null, reminderService.memberDeadline(motive), rc.getStatus().name(), settled, open,
            options(motive));
    }

    /** action : ACK, UNSURE, PROMISE (avec date) ou DONE. */
    @Transactional
    public ActionResult act(String token, String action, LocalDate date) {
        ReminderCase rc = caseFor(token, AccessTokenPurpose.REMINDER_ACTION);
        ReminderResponseType type;
        try {
            type = ReminderResponseType.valueOf(action == null ? "" : action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("action", "Action inconnue.");
        }
        if (type != ReminderResponseType.ACK && type != ReminderResponseType.UNSURE && type != ReminderResponseType.PROMISE
            && type != ReminderResponseType.DONE) {
            throw new InvalidRequestException("action", "Action inconnue.");
        }
        if (type == ReminderResponseType.PROMISE && date == null) {
            throw new InvalidRequestException("date", "Choisissez une date.");
        }
        ReminderResponseService.Outcome outcome = responseService.respond(rc.getId(), ReminderChannel.EMAIL, type, date, null,
            "Page de réponse : " + type.name().toLowerCase() + (date == null ? "" : " " + date), null).orElseThrow();
        return new ActionResult(outcome.reply(), outcome.status().name(),
            outcome.asksForDate() ? options(ReminderMotive.of(rc)) : List.of());
    }

    @Transactional(readOnly = true)
    public ContributionLink contributionLink(String token) {
        AccessToken t = tokenService.resolve(token, AccessTokenPurpose.CONTRIBUTE)
            .orElseThrow(() -> new EntityNotFoundException(INVALID));
        ReminderCase rc = t.getReminderCase();
        if (rc != null && rc.getReason() == ReminderReason.PROJECT) {
            return new ContributionLink(ReminderReason.PROJECT.name(), t.getDonor().getId(), t.getDonor().getDisplayName(),
                null, List.of(), rc.getProject().getId(), rc.getProject().getName());
        }
        MonthlyContributionSettings amounts = reminderService.monthlySettings();
        return new ContributionLink(ReminderReason.MONTHLY_CONTRIBUTION.name(), t.getDonor().getId(),
            t.getDonor().getDisplayName(), amounts.suggestedAmount(t.getDonor().getMemberRole()),
            List.of(YearMonth.from(t.getPeriodMonth()).toString()), null, null);
    }

    private ReminderCase caseFor(String token, AccessTokenPurpose purpose) {
        AccessToken t = tokenService.resolve(token, purpose).orElseThrow(() -> new EntityNotFoundException(INVALID));
        return caseRepository.findById(t.getReminderCase().getId()).orElseThrow(() -> new EntityNotFoundException(INVALID));
    }

    /**
     * Don à une collecte ouvert depuis « Participer maintenant » : le membre du jeton, si le jeton est valide et vise
     * bien cette collecte. Sinon rien (le don reste un don public ordinaire).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<org.mla.finance.domain.Donor> memberForProjectDonation(String token, Long projectId) {
        if (token == null || projectId == null) {
            return java.util.Optional.empty();
        }
        return tokenService.resolve(token, AccessTokenPurpose.CONTRIBUTE)
            .filter(t -> t.getReminderCase() != null && t.getReminderCase().getReason() == ReminderReason.PROJECT
                && projectId.equals(t.getReminderCase().getProject().getId()))
            .map(AccessToken::getDonor);
    }

    private List<DateOption> options(ReminderMotive motive) {
        return responseService.dateChoices(motive).stream()
            .map(c -> new DateOption(c.date(), c.description() + " — " + c.title())).toList();
    }
}
