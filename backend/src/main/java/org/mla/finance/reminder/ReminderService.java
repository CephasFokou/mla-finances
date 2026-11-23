package org.mla.finance.reminder;

import jakarta.persistence.EntityNotFoundException;
import org.mla.finance.domain.AccessTokenPurpose;
import org.mla.finance.domain.Contribution;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MemberStatus;
import org.mla.finance.domain.MonthlyContributionSettings;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.domain.Reminder;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderCaseStatus;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderDelivery;
import org.mla.finance.domain.ReminderDeliveryStatus;
import org.mla.finance.domain.ReminderKind;
import org.mla.finance.domain.ReminderReason;
import org.mla.finance.domain.ReminderSettings;
import org.mla.finance.domain.ReminderTrigger;
import org.mla.finance.domain.User;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.payment.payout.MsisdnMask;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.MonthlyContributionSettingsRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.ReminderCaseRepository;
import org.mla.finance.repository.ReminderDeliveryRepository;
import org.mla.finance.repository.ReminderRepository;
import org.mla.finance.repository.ReminderResponseRepository;
import org.mla.finance.repository.ReminderSettingsRepository;
import org.mla.finance.service.AuditLogService;
import org.mla.finance.service.CurrentUserProvider;
import org.mla.finance.web.dto.ReminderBulkResponse;
import org.mla.finance.web.dto.ReminderCaseDetailResponse;
import org.mla.finance.web.dto.ReminderOverviewResponse;
import org.mla.finance.web.dto.ReminderPreviewResponse;
import org.mla.finance.web.dto.ReminderSendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relances de la contribution mensuelle (cadrage Relances du 20/11/2026, lot 1).
 * <ul>
 *   <li>RL-01 : un membre actif, le mois en cours, sans contribution enregistrée pour ce mois.</li>
 *   <li>RL-03 : tous les canaux disponibles du membre (email au lot 1 ; WhatsApp au lot 2).</li>
 *   <li>RL-04 / §3 : délai d'attente après chaque relance ({@code next_allowed_at}), compté depuis l'envoi.</li>
 *   <li>RL-05 : au plus {@code max_per_month} relances par membre et par mois.</li>
 *   <li>RL-06 : ouvertes à partir du {@code open_from_day} du mois ; échéance le dernier jour du mois.</li>
 *   <li>RL-07 : envois entre 8 h et 20 h (Africa/Douala) ; hors plage, mise en file pour l'ouverture suivante.</li>
 *   <li>RL-08 : toute contribution mensuelle enregistrée clôt le dossier en « Réglé » (événement après commit).</li>
 * </ul>
 * Deux admins qui cliquent en même temps : le dossier est lu sous verrou de ligne, le second voit le délai
 * d'attente posé par le premier (409) ; à la création, la contrainte d'unicité du dossier tranche.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final Set<ReminderCaseStatus> CLOSED = EnumSet.of(ReminderCaseStatus.REGLE, ReminderCaseStatus.SANS_SUITE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm");
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("dd/MM");

    private final ReminderSettingsRepository settingsRepository;
    private final ReminderCaseRepository caseRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final DonorRepository donorRepository;
    private final ContributionRepository contributionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MonthlyContributionSettingsRepository monthlySettingsRepository;
    private final ReminderMessageRenderer renderer;
    private final List<NotificationChannel> channels;
    private final ReminderClock clock;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate requiresNew;
    private final AccessTokenService tokenService;
    private final ReminderResponseRepository responseRepository;
    private final ProjectRepository projectRepository;

    public ReminderService(ReminderSettingsRepository settingsRepository, ReminderCaseRepository caseRepository,
                           ReminderRepository reminderRepository, ReminderDeliveryRepository deliveryRepository,
                           DonorRepository donorRepository, ContributionRepository contributionRepository,
                           PaymentTransactionRepository paymentTransactionRepository,
                           MonthlyContributionSettingsRepository monthlySettingsRepository,
                           ReminderMessageRenderer renderer, List<NotificationChannel> channels, ReminderClock clock,
                           AuditLogService auditLogService, CurrentUserProvider currentUserProvider,
                           PlatformTransactionManager transactionManager, AccessTokenService tokenService,
                           ReminderResponseRepository responseRepository, ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.tokenService = tokenService;
        this.responseRepository = responseRepository;
        this.settingsRepository = settingsRepository;
        this.caseRepository = caseRepository;
        this.reminderRepository = reminderRepository;
        this.deliveryRepository = deliveryRepository;
        this.donorRepository = donorRepository;
        this.contributionRepository = contributionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.monthlySettingsRepository = monthlySettingsRepository;
        this.renderer = renderer;
        this.channels = channels;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public ReminderOverviewResponse overview(String monthParam, Long projectId) {
        Instant now = clock.now();
        ReminderMotive motive = motive(monthParam, projectId, now);
        ReminderSettings settings = settings();
        MonthlyContributionSettings amounts = monthlySettings();

        List<Donor> members = donorRepository.findByMemberRoleIsNotNullAndMemberStatus(MemberStatus.ACTIVE).stream()
            .filter(d -> isTargeted(motive, d))
            .sorted(Comparator.comparing(d -> d.getLabel() == null ? "" : d.getLabel().toLowerCase()))
            .toList();
        Set<Long> paid = new HashSet<>(paidDonors(motive));
        Set<Long> inProgress = new HashSet<>(inProgressDonors(motive));
        List<ReminderCase> motiveCases = motive.isProject()
            ? caseRepository.findByReasonAndProjectId(ReminderReason.PROJECT, motive.projectId())
            : caseRepository.findByReasonAndPeriodMonth(ReminderReason.MONTHLY_CONTRIBUTION, motive.periodMonth());
        Map<Long, ReminderCase> cases = motiveCases.stream()
            .collect(Collectors.toMap(c -> c.getDonor().getId(), Function.identity()));
        Map<Long, List<Reminder>> remindersByCase = cases.isEmpty() ? Map.of()
            : reminderRepository.findByReminderCaseIdIn(cases.values().stream().map(ReminderCase::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getReminderCase().getId()));
        List<Long> reminderIds = remindersByCase.values().stream().flatMap(List::stream).map(Reminder::getId).toList();
        Map<Long, List<ReminderDelivery>> deliveriesByReminder = reminderIds.isEmpty() ? Map.of()
            : deliveryRepository.findByReminderIdInOrderByIdAsc(reminderIds).stream()
                .collect(Collectors.groupingBy(d -> d.getReminder().getId()));

        List<ReminderOverviewResponse.Row> rows = new ArrayList<>();
        int upToDate = 0;
        int unpaid = 0;
        int pending = 0;
        int remindable = 0;
        int sentCount = 0;
        int maxReached = 0;
        for (Donor donor : members) {
            ReminderCase rc = cases.get(donor.getId());
            List<Reminder> reminders = rc == null ? List.of() : remindersByCase.getOrDefault(rc.getId(), List.of());
            Facts facts = new Facts(paid.contains(donor.getId()), inProgress.contains(donor.getId()), targets(donor),
                hasPhone(donor), reminders.stream().anyMatch(Reminder::isQueued));
            Optional<Blocked> block = evaluate(settings, motive, now, facts, rc, donor);

            Reminder last = reminders.stream().filter(r -> r.getCancelledAt() == null)
                .max(Comparator.comparingInt(Reminder::getSequenceNo)).orElse(null);
            List<ReminderOverviewResponse.DeliverySummary> lastDeliveries = last == null ? List.of()
                : deliveriesByReminder.getOrDefault(last.getId(), List.of()).stream()
                    .map(d -> new ReminderOverviewResponse.DeliverySummary(d.getChannel(), d.getStatus())).toList();
            sentCount += (int) reminders.stream().filter(r -> r.getSentAt() != null).count();

            String monthStatus = facts.paid() ? "PAID" : facts.inProgress() ? "IN_PROGRESS" : "UNPAID";
            if (facts.paid()) {
                upToDate++;
            } else {
                unpaid++;
                if (facts.inProgress()) {
                    pending++;
                }
            }
            if (block.isEmpty()) {
                remindable++;
            } else if (block.get().code() == ReminderBlock.MAX_REACHED) {
                maxReached++;
            }
            rows.add(new ReminderOverviewResponse.Row(
                donor.getId(), donor.getLabel(), donor.getMemberRole(),
                motive.isProject() ? null : amounts.suggestedAmount(donor.getMemberRole()),
                monthStatus, EmailChannel.emailOf(donor).isPresent(), hasPhone(donor),
                rc == null ? null : rc.getId(), rc == null ? null : rc.getStatus(), rc == null ? 0 : rc.getReminderCount(),
                last == null ? null : last.getSentAt(), last == null ? null : last.getScheduledFor(), lastDeliveries,
                rc == null ? null : rc.getNextAllowedAt(), block.isEmpty(),
                block.map(b -> b.code().name()).orElse(null), block.map(Blocked::message).orElse(null),
                rc != null && rc.isNeedsAttention(), rc == null ? null : rc.getLastResponse(),
                rc == null ? null : rc.getPromisedDate()));
        }
        LocalDate today = today(now);
        boolean current = motive.isProject() ? motive.isRemindable() && !motive.isOver(today)
            : motive.month().equals(currentMonth(now));
        return new ReminderOverviewResponse(motive.isProject() ? null : motive.month().toString(), motive.label(),
            current, settings.isEnabled(), motive.openFrom(settings), motive.dueDate(), settings.getMaxPerMonth(),
            motive.reason(), motive.projectId(), motive.isProject() ? ReminderMessageRenderer.projectNote(motive.project()) : null,
            new ReminderOverviewResponse.Summary(members.size(), upToDate, unpaid, pending, remindable, sentCount, maxReached),
            rows);
    }

    @Transactional(readOnly = true)
    public ReminderPreviewResponse preview(Long donorId, String monthParam, Long projectId) {
        Instant now = clock.now();
        ReminderMotive motive = motive(monthParam, projectId, now);
        Donor donor = findDonor(donorId);
        ReminderMessage message = message(donor, motive, ReminderLinks.preview(), false);
        ReminderCase rc = caseRepository.findByDonorIdAndReasonAndCaseKey(donorId, motive.reason(), motive.caseKey()).orElse(null);
        Facts facts = facts(donor, motive, rc);
        Optional<Blocked> block = isActiveMember(donor) ? evaluate(settings(), motive, now, facts, rc, donor)
            : Optional.of(new Blocked(ReminderBlock.CLOSED, "Ce donateur n'est pas un membre actif du MLA."));
        return new ReminderPreviewResponse(message.subject(), message.html(), message.text(),
            ReminderMessageRenderer.whatsappText(message.whatsapp()),
            facts.targets().stream().map(t -> new ReminderPreviewResponse.Target(t.channel().channel(), mask(t))).toList(),
            block.isEmpty(), block.map(Blocked::message).orElse(null));
    }

    @Transactional(readOnly = true)
    public ReminderCaseDetailResponse detail(Long caseId) {
        ReminderCase rc = caseRepository.findById(caseId)
            .orElseThrow(() -> new EntityNotFoundException("Dossier de relance introuvable : " + caseId));
        List<Reminder> reminders = reminderRepository.findByReminderCaseIdOrderBySequenceNoAsc(caseId);
        Map<Long, List<ReminderDelivery>> deliveries = reminders.isEmpty() ? Map.of()
            : deliveryRepository.findByReminderIdInOrderByIdAsc(reminders.stream().map(Reminder::getId).toList()).stream()
                .collect(Collectors.groupingBy(d -> d.getReminder().getId()));
        List<ReminderCaseDetailResponse.Entry> entries = reminders.stream().map(r -> new ReminderCaseDetailResponse.Entry(
            r.getId(), r.getSequenceNo(), r.getTrigger(), r.getKind().name(), r.getSentBy() == null ? null : r.getSentBy().getFullName(),
            r.getCreatedAt(), r.getScheduledFor(), r.getSentAt(), r.getCancelledAt(), r.getMessageSnapshot(),
            deliveries.getOrDefault(r.getId(), List.of()).stream().map(d -> new ReminderCaseDetailResponse.Delivery(
                d.getChannel(), maskRecipient(d.getChannel(), d.getRecipient()), d.getStatus(), d.getError(), d.getUpdatedAt()))
                .toList())).toList();
        ReminderMotive motive = ReminderMotive.of(rc);
        return new ReminderCaseDetailResponse(rc.getId(), rc.getDonor().getId(), rc.getDonor().getLabel(), rc.getReason(),
            motive.isProject() ? null : motive.month().toString(), motive.label(),
            rc.getStatus(), rc.getReminderCount(), rc.getNextAllowedAt(), rc.getCreatedAt(), rc.getClosedAt(),
            rc.getClosedReason(), rc.getClosedBy() == null ? null : rc.getClosedBy().getFullName(),
            rc.getContribution() == null ? null : rc.getContribution().getId(), rc.isNeedsAttention(), rc.getPromisedDate(),
            entries, responseRepository.findByReminderCaseIdOrderByReceivedAtAsc(caseId).stream()
                .map(x -> new ReminderCaseDetailResponse.Response(x.getChannel(), x.getType().name(), x.getPromisedDate(),
                    x.getRawText(), x.getOutcome(), x.getReceivedAt())).toList());
    }

    // ------------------------------------------------------------------------------------------------ envoi

    /** Relance d'un membre ; motif = {@code month} (« 2026-11 ») ou {@code projectId} (collecte relançable). */
    @Transactional
    public ReminderSendResponse send(Long donorId, String monthParam, Long projectId, ReminderTrigger trigger) {
        Instant now = clock.now();
        ReminderMotive motive = motive(monthParam, projectId, now);
        ReminderSettings settings = settings();
        Donor donor = findDonor(donorId);
        if (!isActiveMember(donor)) {
            throw new BusinessRuleViolationException("RL-01", "Seuls les membres actifs du MLA sont relancés.");
        }
        ReminderCase rc = lockOrCreateCase(donor, motive);
        Facts facts = facts(donor, motive, rc);
        Optional<Blocked> block = evaluate(settings, motive, now, facts, rc, donor);
        if (block.isPresent()) {
            Blocked b = block.get();
            if (b.code().conflict()) {
                throw new ConflictException(b.message());
            }
            throw new BusinessRuleViolationException(b.code().rule(), b.message());
        }

        boolean inWindow = inWindow(now, settings);
        Instant scheduledFor = inWindow ? null : nextWindowStart(now, settings);
        if (scheduledFor != null && motive.isOver(today(scheduledFor))) {
            throw new BusinessRuleViolationException("RL-07",
                "Hors plage horaire (" + settings.getSendWindowStart() + " - " + settings.getSendWindowEnd()
                    + ") et l'échéance tombe avant la prochaine ouverture : relance impossible.");
        }

        ReminderMessage message = message(donor, motive, inWindow ? links(rc) : ReminderLinks.preview(), false);
        User actor = currentUserProvider.currentOrNull();
        Reminder reminder = reminderRepository.save(Reminder.builder()
            .reminderCase(rc)
            .sequenceNo((int) reminderRepository.countByReminderCaseId(rc.getId()) + 1)
            .trigger(trigger)
            .sentBy(actor)
            .scheduledFor(scheduledFor)
            .messageSnapshot(message.text())
            .build());

        List<ReminderDelivery> deliveries = List.of();
        boolean delivered;
        if (inWindow) {
            deliveries = dispatch(reminder, facts.targets(), message);
            delivered = deliveries.stream().anyMatch(d -> d.getStatus() != ReminderDeliveryStatus.FAILED);
            reminder.setSentAt(now);
        } else {
            delivered = false;
        }

        boolean counts = !inWindow || delivered;
        if (counts) {
            rc.setReminderCount(rc.getReminderCount() + 1);
            rc.setStatus(ReminderCaseStatus.EN_ATTENTE_REPONSE);
            rc.setNextAllowedAt((inWindow ? now : scheduledFor).plus(Duration.ofDays(settings.getCooldownNoResponseDays())));
            rc.setPromisedDate(null);
            rc.setNeedsAttention(false);
            // Dossier « Réglé » rouvert : la contribution qui l'avait clos a été annulée depuis.
            rc.setClosedAt(null);
            rc.setClosedReason(null);
            rc.setClosedBy(null);
            rc.setContribution(null);
        }

        String channelsLabel = facts.targets().stream().map(t -> t.channel().channel().name().toLowerCase())
            .collect(Collectors.joining(", "));
        String what = "Relance n°" + rc.getReminderCount() + "/" + settings.getMaxPerMonth() + " de " + donor.getLabel()
            + " pour " + motive.auditPhrase();
        if (!inWindow) {
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_SENT",
                what + " — programmée pour le " + scheduledFor.atZone(ReminderClock.ZONE).format(DAY_TIME) + " (" + channelsLabel + ")");
        } else if (delivered) {
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_SENT", what + " (" + channelsLabel + ")");
        } else {
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_FAILED",
                "Relance de " + donor.getLabel() + " non envoyée : aucun canal n'a abouti (" + channelsLabel + ")");
        }
        return new ReminderSendResponse(rc.getId(), reminder.getSequenceNo(), delivered, !inWindow, scheduledFor,
            rc.getNextAllowedAt(), deliveries.stream().map(d -> new ReminderSendResponse.Delivery(d.getChannel(),
                maskRecipient(d.getChannel(), d.getRecipient()), d.getStatus(), d.getError())).toList());
    }

    /**
     * Relance groupée (RL-11) : chaque membre dans sa propre transaction, selon ses propres règles ; un membre non
     * éligible est ignoré et listé avec son motif, sans bloquer les autres.
     */
    public ReminderBulkResponse bulk(List<Long> donorIds, String monthParam, Long projectId) {
        if (projectId == null) {
            parseMonth(monthParam);
        }
        List<ReminderBulkResponse.Sent> sent = new ArrayList<>();
        List<ReminderBulkResponse.Skipped> skipped = new ArrayList<>();
        for (Long donorId : new LinkedHashSet<>(donorIds)) {
            String label = donorRepository.findById(donorId).map(Donor::getLabel).orElse("#" + donorId);
            try {
                ReminderSendResponse result = requiresNew.execute(status -> send(donorId, monthParam, projectId, ReminderTrigger.BULK));
                if (result.sent() || result.queued()) {
                    sent.add(new ReminderBulkResponse.Sent(donorId, label, result.sent(), result.queued()));
                } else {
                    skipped.add(new ReminderBulkResponse.Skipped(donorId, label, "Aucun canal n'a abouti : "
                        + result.deliveries().stream().map(ReminderSendResponse.Delivery::error)
                            .filter(e -> e != null).findFirst().orElse("échec d'envoi")));
                }
            } catch (ConflictException | BusinessRuleViolationException | EntityNotFoundException e) {
                skipped.add(new ReminderBulkResponse.Skipped(donorId, label, e.getMessage()));
            } catch (DataIntegrityViolationException e) {
                skipped.add(new ReminderBulkResponse.Skipped(donorId, label, "Relance simultanée en cours pour ce membre."));
            }
        }
        return new ReminderBulkResponse(sent, skipped);
    }

    /**
     * Envoie les relances mises en file dont l'heure est arrivée (ReminderQueueJob). Chaque relance est revérifiée
     * au moment de partir : si le membre a payé entre-temps ou si le dossier a été clos, elle est annulée.
     */
    public int sendDueQueued() {
        Instant now = clock.now();
        List<Long> due = reminderRepository.findDueQueuedIds(now, PageRequest.of(0, 100));
        int processed = 0;
        for (Long id : due) {
            try {
                requiresNew.executeWithoutResult(status -> sendQueued(id, now));
                processed++;
            } catch (RuntimeException e) {
                log.error("Relance programmée #{} : envoi interrompu ({})", id, e.getMessage(), e);
            }
        }
        return processed;
    }

    private void sendQueued(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findById(reminderId).orElse(null);
        if (reminder == null) {
            return;
        }
        ReminderCase rc = caseRepository.findByIdForUpdate(reminder.getReminderCase().getId()).orElseThrow();
        reminder = reminderRepository.findById(reminderId).orElseThrow();
        if (!reminder.isQueued()) {
            return;
        }
        ReminderSettings settings = settings();
        ReminderMotive motive = ReminderMotive.of(rc);
        Donor donor = rc.getDonor();
        Facts facts = facts(donor, motive, rc);
        String cancel = null;
        if (rc.isClosed()) {
            cancel = "dossier clos";
        } else if (facts.paid()) {
            cancel = "contribution enregistrée entre-temps";
        } else if (!settings.isEnabled()) {
            cancel = "relances désactivées";
        } else if (!isActiveMember(donor)) {
            cancel = "membre inactif";
        } else if (motive.isOver(today(now))) {
            cancel = motive.isProject() ? "échéance de la collecte dépassée" : "mois terminé";
        } else if (!motive.isRemindable()) {
            cancel = "relances de la collecte désactivées";
        } else if (!isTargeted(motive, donor)) {
            cancel = "membre plus concerné par la collecte";
        } else if (facts.targets().isEmpty()) {
            cancel = "aucun contact";
        }
        if (cancel != null) {
            cancelQueued(rc, reminder, now);
            if (facts.paid()) {
                closeAsPaid(rc, now);
            }
            log.info("Relance programmée #{} annulée : {}", reminderId, cancel);
            return;
        }
        boolean promise = reminder.getKind() == ReminderKind.PROMISE;
        ReminderMessage message = message(donor, motive, links(rc), promise);
        reminder.setMessageSnapshot(message.text());
        List<ReminderDelivery> deliveries = dispatch(reminder, facts.targets(), message);
        reminder.setSentAt(now);
        boolean delivered = deliveries.stream().anyMatch(d -> d.getStatus() != ReminderDeliveryStatus.FAILED);
        if (delivered && promise) {
            // Rappel du jour promis : le bouton de l'admin reste débloqué le lendemain de la date promise.
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_SENT",
                "Rappel automatique (date promise) envoyé à " + donor.getLabel());
        } else if (delivered) {
            rc.setNextAllowedAt(now.plus(Duration.ofDays(settings.getCooldownNoResponseDays())));
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_SENT",
                "Relance programmée n°" + rc.getReminderCount() + " envoyée à " + donor.getLabel());
        } else {
            // Aucun canal n'a abouti : la relance ne compte pas et le bouton redevient disponible tout de suite.
            rc.setReminderCount(Math.max(0, rc.getReminderCount() - 1));
            if (!promise) {
                rc.setNextAllowedAt(now);
            }
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_FAILED",
                "Relance programmée de " + donor.getLabel() + " non envoyée : aucun canal n'a abouti");
        }
    }

    // ------------------------------------------------------------------------------------------------ clôture

    @Transactional
    public ReminderCaseDetailResponse close(Long caseId, String reason) {
        ReminderCase rc = caseRepository.findByIdForUpdate(caseId)
            .orElseThrow(() -> new EntityNotFoundException("Dossier de relance introuvable : " + caseId));
        closeManually(rc, reason);
        return detail(caseId);
    }

    @Transactional
    public ReminderCaseDetailResponse closeForMember(Long donorId, String monthParam, Long projectId, String reason) {
        ReminderMotive motive = motive(monthParam, projectId, clock.now());
        Donor donor = findDonor(donorId);
        ReminderCase rc = lockOrCreateCase(donor, motive);
        closeManually(rc, reason);
        return detail(rc.getId());
    }

    private void closeManually(ReminderCase rc, String reason) {
        if (rc.isClosed()) {
            throw new ConflictException("Ce dossier est déjà clos.");
        }
        Instant now = clock.now();
        rc.setStatus(ReminderCaseStatus.SANS_SUITE);
        rc.setClosedAt(now);
        rc.setClosedReason(reason.trim());
        rc.setClosedBy(currentUserProvider.currentOrNull());
        reminderRepository.findQueuedByCase(rc.getId()).forEach(r -> cancelQueued(rc, r, now));
        auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_CLOSED",
            "Dossier de relance de " + rc.getDonor().getLabel() + " clos sans suite : " + reason.trim());
    }

    /**
     * RL-08 : une contribution mensuelle enregistrée (saisie, don en ligne confirmé, paiement manuel confirmé) clôt
     * le dossier du membre pour ce mois en « Réglé », quel que soit son état. Après commit, dans sa propre
     * transaction : un échec ici n'annule jamais la contribution.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContributionRecorded(ContributionRecordedEvent event) {
        if (event.donorId() == null) {
            return;
        }
        if (event.projectId() != null) {
            try {
                caseRepository.findForUpdate(event.donorId(), ReminderReason.PROJECT, ReminderMotive.projectKey(event.projectId()))
                    .filter(rc -> !rc.isClosed())
                    .filter(rc -> contributionRepository.donorsWithProjectContribution(event.projectId()).contains(event.donorId()))
                    .ifPresent(rc -> closeAsPaid(rc, clock.now()));
            } catch (RuntimeException e) {
                log.error("Relances : clôture automatique impossible pour le don #{} ({})", event.contributionId(), e.getMessage(), e);
            }
            return;
        }
        if (event.nature() != MovementNature.MONTHLY_CONTRIBUTION || event.periodMonth() == null) {
            return;
        }
        try {
            caseRepository.findForUpdate(event.donorId(), ReminderReason.MONTHLY_CONTRIBUTION,
                    ReminderCase.monthKey(event.periodMonth()))
                .filter(rc -> !rc.isClosed())
                .filter(rc -> contributionRepository.donorsWithMonthlyContribution(event.periodMonth()).contains(event.donorId()))
                .ifPresent(rc -> closeAsPaid(rc, clock.now()));
        } catch (RuntimeException e) {
            log.error("Relances : clôture automatique impossible pour la contribution #{} ({})",
                event.contributionId(), e.getMessage(), e);
        }
    }

    /** Mois terminés : dossiers encore ouverts passés « Sans suite » (ou « Réglé » si payé), file vidée. */
    @Transactional
    public int closeFinishedMonths() {
        Instant now = clock.now();
        LocalDate current = currentMonth(now).atDay(1);
        List<ReminderCase> open = caseRepository.findByReasonAndPeriodMonthBeforeAndStatusNotIn(
            ReminderReason.MONTHLY_CONTRIBUTION, current, CLOSED);
        for (ReminderCase rc : open) {
            if (contributionRepository.donorsWithMonthlyContribution(rc.getPeriodMonth()).contains(rc.getDonor().getId())) {
                closeAsPaid(rc, now);
                continue;
            }
            rc.setStatus(ReminderCaseStatus.SANS_SUITE);
            rc.setClosedAt(now);
            rc.setClosedReason("Mois terminé sans contribution enregistrée");
            reminderRepository.findQueuedByCase(rc.getId()).forEach(r -> cancelQueued(rc, r, now));
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_CLOSED",
                "Dossier de relance de " + rc.getDonor().getLabel() + " clos sans suite (mois terminé)");
        }
        int closed = open.size();
        // Collectes : échéance dépassée, collecte terminée ou relances coupées sans passer par le formulaire.
        LocalDate today = today(now);
        for (ReminderCase rc : caseRepository.findByReasonAndStatusNotIn(ReminderReason.PROJECT, CLOSED)) {
            ReminderMotive motive = ReminderMotive.of(rc);
            if (!motive.isOver(today) && motive.isRemindable()) {
                continue;
            }
            closeProjectCase(rc, motive.isOver(today) ? "Échéance de la collecte dépassée" : "Relances de la collecte arrêtées", now);
            closed++;
        }
        return closed;
    }

    /**
     * Collecte clôturée, archivée, ou relances désactivées (cadrage §6 bis) : ses dossiers ouverts passent « Sans suite »
     * (« Réglé » si le membre a donné) et les rappels programmés sont annulés. Appelé dans la transaction du projet.
     */
    @Transactional
    public int closeProjectCases(Long projectId, String reason) {
        Instant now = clock.now();
        List<ReminderCase> open = caseRepository.findByProjectIdAndStatusNotIn(projectId, CLOSED);
        open.forEach(rc -> closeProjectCase(rc, reason, now));
        return open.size();
    }

    /**
     * Après modification du bloc « Relances » d'une collecte : relances coupées → tous les dossiers ouverts clos ;
     * sinon, ceux des membres sortis de la cible.
     */
    @Transactional
    public int syncProjectCases(Long projectId) {
        Instant now = clock.now();
        List<ReminderCase> open = caseRepository.findByProjectIdAndStatusNotIn(projectId, CLOSED);
        int closed = 0;
        for (ReminderCase rc : open) {
            ReminderMotive motive = ReminderMotive.of(rc);
            if (!motive.isRemindable()) {
                closeProjectCase(rc, "Relances de la collecte désactivées", now);
                closed++;
            } else if (!isTargeted(motive, rc.getDonor())) {
                closeProjectCase(rc, "Membre retiré des relances de la collecte", now);
                closed++;
            }
        }
        return closed;
    }

    private void closeProjectCase(ReminderCase rc, String reason, Instant now) {
        if (contributionRepository.donorsWithProjectContribution(rc.getProject().getId()).contains(rc.getDonor().getId())) {
            closeAsPaid(rc, now);
            return;
        }
        rc.setStatus(ReminderCaseStatus.SANS_SUITE);
        rc.setClosedAt(now);
        rc.setClosedReason(reason);
        rc.setNeedsAttention(false);
        reminderRepository.findQueuedByCase(rc.getId()).forEach(r -> cancelQueued(rc, r, now));
        auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_CLOSED",
            "Dossier de relance de " + rc.getDonor().getLabel() + " (collecte « " + rc.getProject().getName()
                + " ») clos sans suite : " + reason);
    }

    public void closeAsPaid(ReminderCase rc, Instant now) {
        List<Contribution> entries = rc.getReason() == ReminderReason.PROJECT
            ? contributionRepository.projectEntries(rc.getDonor().getId(), rc.getProject().getId())
            : contributionRepository.monthlyEntries(rc.getDonor().getId(), rc.getPeriodMonth());
        Contribution closing = entries.stream().filter(c -> c.getAmount().signum() > 0).findFirst().orElse(null);
        rc.setStatus(ReminderCaseStatus.REGLE);
        rc.setClosedAt(now);
        rc.setClosedReason("Contribution enregistrée");
        rc.setClosedBy(null);
        rc.setContribution(closing);
        rc.setNeedsAttention(false);
        reminderRepository.findQueuedByCase(rc.getId()).forEach(r -> cancelQueued(rc, r, now));
        auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_CLOSED",
            "Dossier de relance de " + rc.getDonor().getLabel() + " réglé"
                + (closing == null ? "" : " (contribution #" + closing.getId() + ")"));
    }

    /** Une relance programmée qui ne part pas n'est pas comptée dans le maximum. */
    public void cancelQueued(ReminderCase rc, Reminder reminder, Instant now) {
        if (!reminder.isQueued()) {
            return;
        }
        reminder.setCancelledAt(now);
        rc.setReminderCount(Math.max(0, rc.getReminderCount() - 1));
        if (rc.getNextAllowedAt() != null && rc.getNextAllowedAt().isAfter(now)) {
            rc.setNextAllowedAt(now);
        }
    }

    // ------------------------------------------------------------------------------------------------ règles

    public record Target(NotificationChannel channel, String recipient) {
    }

    /** Faits du mois pour un membre : réglé, paiement en cours, canaux joignables, relance déjà en file. */
    public record Facts(boolean paid, boolean inProgress, List<Target> targets, boolean hasPhone, boolean queued) {
    }

    record Blocked(ReminderBlock code, String message) {
    }

    public Facts facts(Donor donor, ReminderMotive motive, ReminderCase rc) {
        boolean paid = paidDonors(motive).contains(donor.getId());
        boolean inProgress = inProgressDonors(motive).contains(donor.getId());
        boolean queued = rc != null && rc.getId() != null && !reminderRepository.findQueuedByCase(rc.getId()).isEmpty();
        return new Facts(paid, inProgress, targets(donor), hasPhone(donor), queued);
    }

    /** Ordre des contrôles = ordre d'importance du motif affiché au survol du bouton grisé. */
    Optional<Blocked> evaluate(ReminderSettings settings, ReminderMotive motive, Instant now, Facts facts, ReminderCase rc,
                               Donor donor) {
        if (!settings.isEnabled()) {
            return blocked(ReminderBlock.DISABLED, "Les relances sont désactivées (Administration → Relances).");
        }
        LocalDate today = today(now);
        if (motive.isProject()) {
            if (!motive.isRemindable()) {
                return blocked(ReminderBlock.MONTH_NOT_CURRENT, motive.project().isRemindersEnabled()
                    ? "Collecte terminée : plus de relance."
                    : "Relances désactivées pour cette collecte (fiche du projet).");
            }
            if (motive.isOver(today)) {
                return blocked(ReminderBlock.MONTH_NOT_CURRENT, "Échéance de la collecte dépassée"
                    + (motive.dueDate() == null ? "." : " (" + motive.dueDate().format(DAY) + ") : plus de relance."));
            }
            if (!isTargeted(motive, donor)) {
                return blocked(ReminderBlock.CLOSED, "Ce membre n'est pas concerné par les relances de cette collecte.");
            }
        } else {
            YearMonth current = currentMonth(now);
            if (!motive.month().equals(current)) {
                return blocked(ReminderBlock.MONTH_NOT_CURRENT, motive.month().isBefore(current)
                    ? "Mois terminé : seul le mois en cours peut être relancé."
                    : "Seul le mois en cours peut être relancé.");
            }
        }
        LocalDate openFrom = motive.openFrom(settings);
        if (openFrom != null && today.isBefore(openFrom)) {
            return blocked(ReminderBlock.NOT_OPEN_YET, "Relances ouvertes à partir du " + openFrom.format(DAY) + ".");
        }
        if (facts.paid()) {
            return blocked(ReminderBlock.PAID, motive.isProject()
                ? "Participation à la collecte déjà enregistrée."
                : "Contribution de " + motive.label() + " déjà enregistrée.");
        }
        if (rc != null && rc.getStatus() == ReminderCaseStatus.SANS_SUITE) {
            return blocked(ReminderBlock.CLOSED, "Dossier clos sans suite"
                + (rc.getClosedReason() == null ? "." : " : " + rc.getClosedReason()));
        }
        if (facts.inProgress()) {
            return blocked(ReminderBlock.PAYMENT_IN_PROGRESS, (motive.isProject()
                ? "Un don à cette collecte" : "Un paiement de ce mois")
                + " est en cours de validation : pas de relance tant qu'il n'est pas final.");
        }
        if (facts.queued()) {
            return blocked(ReminderBlock.QUEUED,
                "Une relance est déjà programmée : elle partira à l'ouverture de la plage horaire d'envoi.");
        }
        if (rc != null && rc.getStatus() != ReminderCaseStatus.REGLE && rc.getReminderCount() >= settings.getMaxPerMonth()) {
            return blocked(ReminderBlock.MAX_REACHED,
                "Maximum de " + settings.getMaxPerMonth() + " relances atteint pour "
                    + (motive.isProject() ? "cette collecte." : "ce mois."));
        }
        if (rc != null && rc.getStatus() != ReminderCaseStatus.REGLE && rc.getStatus() != ReminderCaseStatus.VERIFICATION
            && rc.getNextAllowedAt() != null && now.isBefore(rc.getNextAllowedAt())) {
            String from = rc.getNextAllowedAt().atZone(ReminderClock.ZONE).format(DAY_TIME);
            String why = switch (rc.getStatus()) {
                case ACCUSE -> "Le membre a répondu « Bien reçu »";
                case PROMIS -> rc.getPromisedDate() == null ? "Le membre a promis une date"
                    : "Le membre prévoit de payer le " + rc.getPromisedDate().format(DAY_SHORT);
                case DECLARE_NON_RETROUVE -> "Le membre dit avoir payé (non retrouvé, à vérifier)";
                default -> "Relancé, sans réponse";
            };
            return blocked(ReminderBlock.COOLDOWN, why + " — relance possible à partir du " + from + ".");
        }
        if (facts.targets().isEmpty()) {
            boolean whatsappEnabled = channels.stream().anyMatch(c -> c.channel() == ReminderChannel.WHATSAPP
                && c.isEnabled());
            return blocked(ReminderBlock.NO_CONTACT, facts.hasPhone() && !whatsappEnabled
                ? "Aucun email renseigné (l'envoi WhatsApp n'est pas encore actif)."
                : "Aucun contact : ni email ni numéro WhatsApp.");
        }
        return Optional.empty();
    }

    private static Optional<Blocked> blocked(ReminderBlock code, String message) {
        return Optional.of(new Blocked(code, message));
    }

    private List<Target> targets(Donor donor) {
        List<Target> result = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            if (channel.isEnabled()) {
                channel.recipientFor(donor).ifPresent(recipient -> result.add(new Target(channel, recipient)));
            }
        }
        return result;
    }

    private List<ReminderDelivery> dispatch(Reminder reminder, List<Target> targets, ReminderMessage message) {
        List<ReminderDelivery> result = new ArrayList<>();
        for (Target target : targets) {
            NotificationChannel.Outcome outcome;
            try {
                outcome = target.channel().send(target.recipient(), message);
            } catch (RuntimeException e) {
                outcome = new NotificationChannel.Outcome(false, null, "Erreur inattendue du canal");
                log.error("Relance #{} : canal {} en erreur", reminder.getId(), target.channel().channel(), e);
            }
            result.add(deliveryRepository.save(ReminderDelivery.builder()
                .reminder(reminder)
                .channel(target.channel().channel())
                .recipient(target.recipient())
                .providerMessageId(outcome.providerMessageId())
                .status(outcome.sent() ? ReminderDeliveryStatus.SENT : ReminderDeliveryStatus.FAILED)
                .error(truncate(outcome.error()))
                .build()));
        }
        return result;
    }

    private ReminderCase lockOrCreateCase(Donor donor, ReminderMotive motive) {
        String key = motive.caseKey();
        Optional<ReminderCase> existing = caseRepository.findForUpdate(donor.getId(), motive.reason(), key);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return caseRepository.saveAndFlush(ReminderCase.builder()
                .donor(donor)
                .reason(motive.reason())
                .periodMonth(motive.periodMonth())
                .project(motive.project())
                .caseKey(key)
                .status(ReminderCaseStatus.A_RELANCER)
                .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Une relance vient d'être envoyée à ce membre par quelqu'un d'autre : actualisez la page.");
        }
    }

    /** Jetons neufs pour les liens d'une relance qui part maintenant (page de réponse, « Contribuer maintenant »). */
    public ReminderLinks links(ReminderCase rc) {
        return new ReminderLinks(rc.getId(), tokenService.issue(AccessTokenPurpose.REMINDER_ACTION, rc),
            tokenService.issue(AccessTokenPurpose.CONTRIBUTE, rc));
    }

    /** « Réponse lue » : retire le dossier du badge et du filtre « Réponse à lire ». */
    @Transactional
    public ReminderCaseDetailResponse acknowledge(Long caseId) {
        ReminderCase rc = caseRepository.findByIdForUpdate(caseId)
            .orElseThrow(() -> new EntityNotFoundException("Dossier de relance introuvable : " + caseId));
        if (rc.isNeedsAttention()) {
            rc.setNeedsAttention(false);
            auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_RESPONSE_READ",
                "Réponse de " + rc.getDonor().getLabel() + " lue");
        }
        return detail(caseId);
    }

    @Transactional(readOnly = true)
    public long attentionCount() {
        return reminderRepository.countNeedingAttention();
    }

    /**
     * Paiement échoué ou abandonné : un dossier « En vérification » sur ce mois redevient relançable tout de suite
     * (cadrage §3, ligne VERIFICATION).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentAbandoned(PaymentAbandonedEvent event) {
        if (event.donorId() == null) {
            return;
        }
        Instant now = clock.now();
        List<Optional<ReminderCase>> found = new ArrayList<>();
        if (event.projectId() != null) {
            found.add(caseRepository.findForUpdate(event.donorId(), ReminderReason.PROJECT, ReminderMotive.projectKey(event.projectId())));
        }
        for (LocalDate month : event.months()) {
            found.add(caseRepository.findForUpdate(event.donorId(), ReminderReason.MONTHLY_CONTRIBUTION, ReminderCase.monthKey(month)));
        }
        for (Optional<ReminderCase> candidate : found) {
            candidate
                .filter(rc -> rc.getStatus() == ReminderCaseStatus.VERIFICATION)
                .ifPresent(rc -> {
                    rc.setStatus(ReminderCaseStatus.EN_ATTENTE_REPONSE);
                    rc.setNextAllowedAt(now);
                    auditLogService.record("REMINDER_CASE", rc.getId(), "REMINDER_REOPENED",
                        "Paiement de " + rc.getDonor().getLabel() + " non abouti : dossier de nouveau relançable");
                });
        }
    }

    // ------------------------------------------------------------------------------------------------ motifs

    /**
     * Motifs proposés sur l'écran Relances (cadrage §9, GET /reminders/reasons) : contribution mensuelle du mois en
     * cours, puis les collectes relançables en cours.
     */
    @Transactional(readOnly = true)
    public List<ReminderReasonOption> reasons() {
        Instant now = clock.now();
        YearMonth month = currentMonth(now);
        List<ReminderReasonOption> result = new ArrayList<>();
        result.add(new ReminderReasonOption(ReminderReason.MONTHLY_CONTRIBUTION, month.toString(), null,
            "Contribution mensuelle — " + ReminderMessageRenderer.monthLabel(month), month.atEndOfMonth()));
        LocalDate today = today(now);
        for (Project project : projectRepository.findByRemindersEnabledTrueAndStatusOrderByNameAsc(ProjectStatus.ACTIVE)) {
            ReminderMotive motive = ReminderMotive.project(project);
            if (!motive.isOver(today)) {
                result.add(new ReminderReasonOption(ReminderReason.PROJECT, null, project.getId(),
                    "Collecte — " + project.getName(), motive.dueDate()));
            }
        }
        return result;
    }

    public record ReminderReasonOption(ReminderReason reason, String month, Long projectId, String label, LocalDate dueDate) {
    }

    /** {@code projectId} renseigné : la collecte ; sinon le mois (« 2026-11 », mois en cours par défaut). */
    ReminderMotive motive(String monthParam, Long projectId, Instant now) {
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Collecte introuvable : " + projectId));
            return ReminderMotive.project(project);
        }
        return ReminderMotive.monthly(monthParam == null || monthParam.isBlank() ? currentMonth(now) : parseMonth(monthParam));
    }

    private List<Long> paidDonors(ReminderMotive motive) {
        return motive.isProject() ? contributionRepository.donorsWithProjectContribution(motive.projectId())
            : contributionRepository.donorsWithMonthlyContribution(motive.periodMonth());
    }

    private List<Long> inProgressDonors(ReminderMotive motive) {
        return motive.isProject() ? paymentTransactionRepository.donorsWithDonationInProgress(motive.projectId())
            : paymentTransactionRepository.donorsWithPaymentInProgress(motive.periodMonth());
    }

    /** Collecte : membre dans la cible choisie par l'admin (tous, responsables, membres, sélection). */
    static boolean isTargeted(ReminderMotive motive, Donor donor) {
        if (!motive.isProject()) {
            return true;
        }
        Project project = motive.project();
        return project.getReminderAudience().includes(donor, project.getReminderMemberIds());
    }

    private ReminderMessage message(Donor donor, ReminderMotive motive, ReminderLinks links, boolean promise) {
        if (motive.isProject()) {
            return renderer.project(donor, motive.project(), motive.dueDate(), links, promise);
        }
        BigDecimal amount = monthlySettings().suggestedAmount(donor.getMemberRole());
        return promise ? renderer.promise(donor, motive.month(), amount, links) : renderer.monthly(donor, motive.month(), amount, links);
    }

    static LocalDate today(Instant now) {
        return now.atZone(ReminderClock.ZONE).toLocalDate();
    }

    // ------------------------------------------------------------------------------------------------ outils

    static boolean inWindow(Instant now, ReminderSettings settings) {
        LocalTime time = now.atZone(ReminderClock.ZONE).toLocalTime();
        return !time.isBefore(settings.getSendWindowStart()) && time.isBefore(settings.getSendWindowEnd());
    }

    static Instant nextWindowStart(Instant now, ReminderSettings settings) {
        ZonedDateTime local = now.atZone(ReminderClock.ZONE);
        LocalDate day = local.toLocalTime().isBefore(settings.getSendWindowStart()) ? local.toLocalDate() : local.toLocalDate().plusDays(1);
        return day.atTime(settings.getSendWindowStart()).atZone(ReminderClock.ZONE).toInstant();
    }

    public static YearMonth currentMonth(Instant now) {
        return YearMonth.from(now.atZone(ReminderClock.ZONE));
    }

    static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRequestException("month", "Mois attendu au format AAAA-MM.");
        }
    }

    public ReminderSettings settings() {
        return settingsRepository.findById(ReminderSettings.SINGLETON_ID).orElseGet(ReminderSettings::new);
    }

    public MonthlyContributionSettings monthlySettings() {
        return monthlySettingsRepository.findById(MonthlyContributionSettings.SINGLETON_ID).orElseGet(MonthlyContributionSettings::new);
    }

    private Donor findDonor(Long donorId) {
        return donorRepository.findById(donorId).orElseThrow(() -> new EntityNotFoundException("Membre introuvable : " + donorId));
    }

    private static boolean isActiveMember(Donor donor) {
        return donor.isMember() && donor.getMemberStatus() == MemberStatus.ACTIVE;
    }

    private static boolean hasPhone(Donor donor) {
        return donor.getPhone() != null && donor.getPhone().replaceAll("\\D", "").length() >= 9;
    }

    private static String mask(Target target) {
        return maskRecipient(target.channel().channel(), target.recipient());
    }

    static String maskRecipient(ReminderChannel channel, String recipient) {
        return channel == ReminderChannel.EMAIL ? EmailChannel.mask(recipient) : MsisdnMask.mask(recipient);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 300 ? value : value.substring(0, 299) + "…";
    }
}
