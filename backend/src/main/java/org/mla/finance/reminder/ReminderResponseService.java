package org.mla.finance.reminder;

import jakarta.persistence.EntityNotFoundException;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.Reminder;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderKind;
import org.mla.finance.domain.ReminderResponse;
import org.mla.finance.domain.ReminderResponseType;
import org.mla.finance.domain.ReminderSettings;
import org.mla.finance.domain.ReminderTrigger;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ReminderCaseRepository;
import org.mla.finance.repository.ReminderRepository;
import org.mla.finance.repository.ReminderResponseRepository;
import org.mla.finance.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Réponses des membres aux relances (cadrage Relances §3-§4), quel que soit le canal : boutons et liste WhatsApp,
 * texte libre WhatsApp, page de réponse des emails.
 * <ul>
 *   <li>« Bien reçu » : dossier ACCUSE (+5 j), puis proposition de dates (jamais après l'échéance).</li>
 *   <li>Date choisie : PROMIS ; rappel automatique ce jour-là à 9 h (compté dans le maximum) et relance de l'admin
 *       possible dès le lendemain.</li>
 *   <li>« C'est déjà fait » : vérification en base, jamais crue sur parole (RL-09) — réglé, paiement en cours
 *       (VERIFICATION), ou non retrouvé (+48 h, alerte aux trésoriers).</li>
 *   <li>Texte libre : mots-clés, date JJ/MM, référence MLA-… ; sinon « Réponse à lire ».</li>
 * </ul>
 * Le dossier est lu sous verrou de ligne : deux réponses simultanées se sérialisent, et un message WhatsApp rejoué
 * (même wamid) n'est traité qu'une fois.
 */
@Service
public class ReminderResponseService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale FR = Locale.FRENCH;
    private static final Pattern REFERENCE = Pattern.compile("(?i)\\bMLA-[A-Z0-9]{4,8}(?:-[A-Z0-9]{4})?\\b");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})(?:\\s*[/.-]\\s*\\d{2,4})?\\b");
    private static final Pattern DAY_ONLY = Pattern.compile("\\ble\\s+(\\d{1,2})\\b");
    private static final Pattern NEGATION = Pattern.compile("\\bpas (encore|fait|paye|verse|envoye)\\b|\\bpas encore\\b");
    private static final Pattern DONE_WORDS = Pattern.compile(
        "\\b(fait|paye|payee|envoye|envoyee|verse|versee|deja|regle|reglee|depose|deposee)\\b");
    private static final Pattern ACK_WORDS = Pattern.compile(
        "\\b(ok|okay|recu|recue|merci|d accord|daccord|note|entendu|compris|bien)\\b");

    private final ReminderService reminderService;
    private final ReminderCaseRepository caseRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderResponseRepository responseRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ReminderClock clock;
    private final AuditLogService auditLogService;
    private final ReminderAlertService alertService;

    public ReminderResponseService(ReminderService reminderService, ReminderCaseRepository caseRepository,
                                   ReminderRepository reminderRepository, ReminderResponseRepository responseRepository,
                                   PaymentTransactionRepository paymentTransactionRepository, ReminderClock clock,
                                   AuditLogService auditLogService, ReminderAlertService alertService) {
        this.reminderService = reminderService;
        this.caseRepository = caseRepository;
        this.reminderRepository = reminderRepository;
        this.responseRepository = responseRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.alertService = alertService;
    }

    /** Date proposée au membre : titre court (liste WhatsApp, 24 caractères au plus) et précision. */
    public record DateChoice(LocalDate date, String title, String description) {
    }

    /**
     * Suite à donner : message à renvoyer au membre (null : rien), dates à proposer s'il faut choisir une date
     * (avec « Je ne sais pas encore »), nouvel état du dossier.
     */
    public record Outcome(ReminderCaseStatus status, String reply, List<DateChoice> dateChoices) {
        public boolean asksForDate() {
            return dateChoices != null && !dateChoices.isEmpty();
        }
    }

    /** Lecture d'un texte libre : type de réponse, date promise ou référence de paiement éventuelles. */
    public record Classified(ReminderResponseType type, LocalDate date, String reference) {
    }

    /**
     * Traite une réponse. {@code providerMessageId} (wamid) déjà vu : rien n'est fait, {@code Optional.empty()}.
     */
    @Transactional
    public Optional<Outcome> respond(Long caseId, ReminderChannel channel, ReminderResponseType type, LocalDate date,
                                     String reference, String rawText, String providerMessageId) {
        ReminderCase rc = caseRepository.findByIdForUpdate(caseId)
            .orElseThrow(() -> new EntityNotFoundException("Dossier de relance introuvable : " + caseId));
        if (providerMessageId != null && responseRepository.existsByProviderMessageId(providerMessageId)) {
            return Optional.empty();
        }
        Instant now = clock.now();
        ReminderSettings settings = reminderService.settings();
        ReminderMotive motive = ReminderMotive.of(rc);
        String what = motive.memberPhrase();
        boolean monthOver = motive.isOver(ReminderService.today(now)) || !motive.isRemindable();

        Step step;
        if (type == ReminderResponseType.DONE || type == ReminderResponseType.REFERENCE) {
            step = type == ReminderResponseType.REFERENCE ? reference(rc, reference, now, settings, motive)
                : verify(rc, now, settings, motive, "Déclare avoir payé");
        } else if (rc.getStatus() == ReminderCaseStatus.REGLE) {
            step = new Step("Merci, " + what + " est déjà enregistrée. 🙏", null, "Dossier déjà réglé");
        } else if (rc.getStatus() == ReminderCaseStatus.SANS_SUITE || monthOver) {
            step = new Step("Merci pour votre réponse.", null, "Dossier clos : réponse enregistrée");
        } else {
            step = switch (type) {
                case ACK -> ack(rc, now, settings, motive);
                case UNSURE -> unsure(rc, now, settings, motive);
                case PROMISE -> promise(rc, date, now, settings, motive);
                default -> freeText(rc);
            };
        }

        rc.setLastResponse(type.name());
        responseRepository.save(ReminderResponse.builder()
            .reminderCase(rc)
            .channel(channel)
            .type(type)
            .promisedDate(type == ReminderResponseType.PROMISE ? date : null)
            .rawText(truncate(rawText, 500))
            .providerMessageId(providerMessageId)
            .outcome(truncate(step.outcome(), 300))
            .receivedAt(now)
            .build());
        auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_RESPONSE",
            "Réponse de " + rc.getDonor().getLabel() + " (" + channel.name().toLowerCase() + ") : " + step.outcome());
        return Optional.of(new Outcome(rc.getStatus(), step.reply(), step.choices()));
    }

    /** Dates proposées après « Bien reçu » : jamais avant demain, jamais après l'échéance (mois ou collecte). */
    public List<DateChoice> dateChoices(ReminderMotive motive) {
        LocalDate today = clock.now().atZone(ReminderClock.ZONE).toLocalDate();
        LocalDate due = motive.dueDate();
        if (due == null || !due.isAfter(today)) {
            return List.of();
        }
        List<DateChoice> choices = new ArrayList<>();
        LocalDate friday = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        add(choices, friday, "Vendredi " + friday.format(DAY), "Ce vendredi", due);
        LocalDate week = today.plusWeeks(1);
        if (week.isBefore(due)) {
            add(choices, week, capitalize(week.getDayOfWeek().getDisplayName(TextStyle.FULL, FR)) + " " + week.format(DAY),
                "Dans une semaine", due);
        }
        if (motive.isProject()) {
            add(choices, due, "Le " + due.format(DAY), "Avant l'échéance de la collecte", due);
        } else {
            LocalDate the25 = motive.month().atDay(25);
            if (the25.isAfter(today)) {
                add(choices, the25, "Le " + the25.format(DAY), "Le 25 du mois", due);
            }
            add(choices, due, "Fin du mois (" + due.format(DAY) + ")", "Avant la fin du mois", due);
        }
        choices.sort((a, b) -> a.date().compareTo(b.date()));
        return choices;
    }

    /** Lecture simple d'un texte libre (cadrage §4.1). Priorité : référence, date, « fait », « bien reçu ». */
    public static Classified classify(String text, YearMonth month) {
        if (text == null || text.isBlank()) {
            return new Classified(ReminderResponseType.FREE_TEXT, null, null);
        }
        Matcher ref = REFERENCE.matcher(text);
        if (ref.find()) {
            return new Classified(ReminderResponseType.REFERENCE, null, ref.group().toUpperCase(Locale.ROOT));
        }
        String n = normalize(text);
        Matcher date = DATE.matcher(n);
        if (date.find()) {
            LocalDate d = safeDate(month.getYear(), Integer.parseInt(date.group(2)), Integer.parseInt(date.group(1)));
            if (d != null) {
                return new Classified(ReminderResponseType.PROMISE, d, null);
            }
        }
        Matcher dayOnly = DAY_ONLY.matcher(n);
        if (dayOnly.find()) {
            LocalDate d = safeDate(month.getYear(), month.getMonthValue(), Integer.parseInt(dayOnly.group(1)));
            if (d != null) {
                return new Classified(ReminderResponseType.PROMISE, d, null);
            }
        }
        if (NEGATION.matcher(n).find()) {
            return new Classified(ReminderResponseType.ACK, null, null);
        }
        if (DONE_WORDS.matcher(n).find()) {
            return new Classified(ReminderResponseType.DONE, null, null);
        }
        if (ACK_WORDS.matcher(n).find()) {
            return new Classified(ReminderResponseType.ACK, null, null);
        }
        return new Classified(ReminderResponseType.FREE_TEXT, null, null);
    }

    // ------------------------------------------------------------------------------------------------ transitions

    private record Step(String reply, List<DateChoice> choices, String outcome) {
    }

    private Step ack(ReminderCase rc, Instant now, ReminderSettings settings, ReminderMotive motive) {
        rc.setStatus(ReminderCaseStatus.ACCUSE);
        rc.setNextAllowedAt(now.plus(Duration.ofDays(settings.getCooldownAckDays())));
        List<DateChoice> choices = dateChoices(motive);
        if (choices.isEmpty()) {
            return new Step("Merci ! Vous pouvez " + (motive.isProject() ? "participer" : "contribuer")
                + " à tout moment avec le bouton « " + contributeLabel(motive) + " ».", null, "Bien reçu");
        }
        return new Step("Merci ! Quand pensez-vous " + (motive.isProject() ? "participer à la collecte « "
            + motive.project().getName() + " »" : "verser " + motive.memberPhrase()) + " ?", choices, "Bien reçu");
    }

    private static String contributeLabel(ReminderMotive motive) {
        return motive.isProject() ? "Participer maintenant" : "Contribuer maintenant";
    }

    private Step unsure(ReminderCase rc, Instant now, ReminderSettings settings, ReminderMotive motive) {
        cancelPromiseReminders(rc, now);
        rc.setStatus(ReminderCaseStatus.ACCUSE);
        rc.setPromisedDate(null);
        rc.setNextAllowedAt(now.plus(Duration.ofDays(settings.getCooldownAckDays())));
        return new Step("C'est noté, merci ! Vous pouvez " + (motive.isProject() ? "participer" : "contribuer")
            + " à tout moment avec le bouton « " + contributeLabel(motive) + " ».",
            null, "Ne sait pas encore quand payer");
    }

    private Step promise(ReminderCase rc, LocalDate date, Instant now, ReminderSettings settings, ReminderMotive motive) {
        LocalDate today = now.atZone(ReminderClock.ZONE).toLocalDate();
        LocalDate due = motive.dueDate();
        if (date == null || date.isBefore(today) || date.isAfter(due)) {
            rc.setNeedsAttention(true);
            return new Step("Merci ! Cette date tombe après l'échéance (le " + due.format(DAY)
                + "). Un membre de la Coordination reviendra vers vous si besoin.", null,
                "Date proposée hors délai (" + (date == null ? "?" : date.format(DAY)) + ") : à lire");
        }
        cancelPromiseReminders(rc, now);
        rc.setStatus(ReminderCaseStatus.PROMIS);
        rc.setPromisedDate(date);
        rc.setNextAllowedAt(date.plusDays(1).atStartOfDay(ReminderClock.ZONE).toInstant());
        Instant at = date.atTime(LocalTime.of(9, 0)).atZone(ReminderClock.ZONE).toInstant();
        boolean scheduled = settings.isAutoReminderOnPromise() && at.isAfter(now)
            && rc.getReminderCount() < settings.getMaxPerMonth();
        if (scheduled) {
            reminderRepository.save(Reminder.builder()
                .reminderCase(rc)
                .sequenceNo((int) reminderRepository.countByReminderCaseId(rc.getId()) + 1)
                .trigger(ReminderTrigger.SCHEDULED)
                .kind(ReminderKind.PROMISE)
                .scheduledFor(at)
                .build());
            rc.setReminderCount(rc.getReminderCount() + 1);
        }
        return new Step(scheduled
            ? "C'est noté : nous vous le rappellerons le " + date.format(DAY) + " si besoin. Merci !"
            : "C'est noté, merci !", null, "Date promise : " + date.format(DAY));
    }

    private Step verify(ReminderCase rc, Instant now, ReminderSettings settings, ReminderMotive motive, String prefix) {
        ReminderService.Facts facts = reminderService.facts(rc.getDonor(), motive, rc);
        if (facts.paid()) {
            if (rc.getStatus() != ReminderCaseStatus.REGLE) {
                reminderService.closeAsPaid(rc, now);
            }
            return new Step("Merci, " + motive.memberPhrase() + " est bien enregistrée. 🙏", null,
                prefix + " : contribution retrouvée");
        }
        cancelPromiseReminders(rc, now);
        if (facts.inProgress()) {
            rc.setStatus(ReminderCaseStatus.VERIFICATION);
            rc.setClosedAt(null);
            return new Step("Votre paiement est en cours de validation, nous vous confirmons dès qu'il est enregistré.", null,
                prefix + " : paiement en cours de validation");
        }
        rc.setStatus(ReminderCaseStatus.DECLARE_NON_RETROUVE);
        rc.setClosedAt(null);
        rc.setNeedsAttention(true);
        rc.setNextAllowedAt(now.plus(Duration.ofHours(settings.getCooldownNotFoundHours())));
        alertService.declaredButNotFound(rc);
        return new Step("Nous ne retrouvons pas encore votre " + (motive.isProject() ? "don" : "contribution") + ". Pouvez-vous nous envoyer la référence de paiement "
            + "(MLA-…) ou nous dire comment vous avez payé (espèces, virement, à qui) ?", null,
            prefix + " : non retrouvé, à vérifier");
    }

    private Step reference(ReminderCase rc, String reference, Instant now, ReminderSettings settings, ReminderMotive motive) {
        Optional<PaymentTransaction> found = reference == null ? Optional.empty()
            : paymentTransactionRepository.findByReference(reference);
        if (found.isEmpty()) {
            rc.setNeedsAttention(true);
            return new Step("Nous ne retrouvons pas la référence " + reference + ". Un membre de la Coordination va vérifier "
                + "et revient vers vous.", null, "Référence " + reference + " introuvable : à vérifier");
        }
        PaymentTransaction tx = found.get();
        if (tx.getDonor() != null && !tx.getDonor().getId().equals(rc.getDonor().getId())) {
            rc.setNeedsAttention(true);
            return new Step("Merci ! Cette référence est enregistrée au nom d'un autre membre : un membre de la Coordination "
                + "va vérifier.", null, "Référence " + reference + " au nom d'un autre membre : à vérifier");
        }
        if (tx.getStatus() == PaymentTransactionStatus.FAILED || tx.getStatus() == PaymentTransactionStatus.CANCELLED) {
            rc.setNeedsAttention(true);
            return new Step("Le paiement " + reference + " n'a pas abouti. Vous pouvez réessayer avec le bouton "
                + "« " + contributeLabel(motive) + " ».", null, "Référence " + reference + " : paiement non abouti");
        }
        Step step = verify(rc, now, settings, motive, "Référence " + reference);
        if (rc.getStatus() == ReminderCaseStatus.DECLARE_NON_RETROUVE) {
            // Paiement retrouvé mais pas pour ce mois (don à une collecte, autre mois…) : un trésorier tranche.
            return new Step("Merci ! Nous avons bien la référence " + reference + " ; un membre de la Coordination vérifie "
                + "qu'elle couvre bien " + motive.memberPhrase() + ".", null,
                "Référence " + reference + " retrouvée mais ne couvre pas " + (motive.isProject() ? "cette collecte" : "ce mois")
                    + " : à vérifier");
        }
        return step;
    }

    private Step freeText(ReminderCase rc) {
        rc.setNeedsAttention(true);
        return new Step("Merci pour votre message, un membre de la Coordination le lira.", null, "Réponse à lire");
    }

    private void cancelPromiseReminders(ReminderCase rc, Instant now) {
        for (Reminder r : reminderRepository.findQueuedByCase(rc.getId())) {
            if (r.getKind() == ReminderKind.PROMISE) {
                reminderService.cancelQueued(rc, r, now);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ outils

    private static void add(List<DateChoice> choices, LocalDate date, String title, String description, LocalDate due) {
        if (date.isAfter(due) || choices.stream().anyMatch(c -> c.date().equals(date))) {
            return;
        }
        choices.add(new DateChoice(date, title, description));
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String normalize(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[’'`]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
