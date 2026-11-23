package org.mla.finance.reminder;

import jakarta.persistence.EntityNotFoundException;
import org.mla.finance.domain.AccessToken;
import org.mla.finance.domain.AccessTokenPurpose;
import org.mla.finance.domain.MonthlyContributionSettings;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
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
    public record ActionView(String name, String month, String monthLabel, LocalDate dueDate, String status,
                             boolean settled, boolean open, List<DateOption> dateOptions) {
    }

    public record ActionResult(String message, String status, List<DateOption> dateOptions) {
    }

    /** Lien « Contribuer maintenant » : le membre et le mois à présélectionner sur le site de don. */
    public record ContributionLink(Long donorId, String displayName, BigDecimal suggestedAmount, List<String> months) {
    }

    @Transactional(readOnly = true)
    public ActionView view(String token) {
        ReminderCase rc = caseFor(token, AccessTokenPurpose.REMINDER_ACTION);
        YearMonth month = YearMonth.from(rc.getPeriodMonth());
        boolean settled = rc.getStatus() == ReminderCaseStatus.REGLE
            || reminderService.facts(rc.getDonor(), month, rc).paid();
        boolean open = !settled && rc.getStatus() != ReminderCaseStatus.SANS_SUITE;
        return new ActionView(rc.getDonor().getLabel(), month.toString(), ReminderMessageRenderer.monthLabel(month),
            month.atEndOfMonth(), rc.getStatus().name(), settled, open, options(month));
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
        YearMonth month = YearMonth.from(rc.getPeriodMonth());
        return new ActionResult(outcome.reply(), outcome.status().name(), outcome.asksForDate() ? options(month) : List.of());
    }

    @Transactional(readOnly = true)
    public ContributionLink contributionLink(String token) {
        AccessToken t = tokenService.resolve(token, AccessTokenPurpose.CONTRIBUTE)
            .orElseThrow(() -> new EntityNotFoundException(INVALID));
        MonthlyContributionSettings amounts = reminderService.monthlySettings();
        return new ContributionLink(t.getDonor().getId(), t.getDonor().getDisplayName(),
            amounts.suggestedAmount(t.getDonor().getMemberRole()),
            List.of(YearMonth.from(t.getPeriodMonth()).toString()));
    }

    private ReminderCase caseFor(String token, AccessTokenPurpose purpose) {
        AccessToken t = tokenService.resolve(token, purpose).orElseThrow(() -> new EntityNotFoundException(INVALID));
        return caseRepository.findById(t.getReminderCase().getId()).orElseThrow(() -> new EntityNotFoundException(INVALID));
    }

    private List<DateOption> options(YearMonth month) {
        return responseService.dateChoices(month).stream()
            .map(c -> new DateOption(c.date(), c.description() + " — " + c.title())).toList();
    }
}
