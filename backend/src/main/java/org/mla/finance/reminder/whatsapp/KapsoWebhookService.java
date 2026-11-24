package org.mla.finance.reminder.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderDelivery;
import org.mla.finance.domain.ReminderDeliveryStatus;
import org.mla.finance.domain.ReminderResponseType;
import org.mla.finance.domain.ReminderWhatsAppReply;
import org.mla.finance.reminder.ReminderClock;
import org.mla.finance.reminder.ReminderPayloads;
import org.mla.finance.reminder.ReminderResponseService;
import org.mla.finance.repository.ReminderCaseRepository;
import org.mla.finance.repository.ReminderDeliveryRepository;
import org.mla.finance.repository.ReminderWhatsAppReplyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Événements WhatsApp reçus de Kapso pour le numéro partagé avec le suivi des tâches MLA.
 * <p>
 * Routage (cadrage §6.2, adapté au 22/11/2026 : le suivi des tâches lit ses réponses lui-même, sans webhook) — un
 * message entrant est pour MLA Finance seulement si :
 * <ol>
 *   <li>c'est un bouton ou une liste dont l'identifiant commence par « mlaf: » ;</li>
 *   <li>ou il répond (context.id) à un message envoyé par MLA Finance ;</li>
 *   <li>ou c'est un texte libre d'un membre à qui MLA Finance a écrit sur WhatsApp ces 7 derniers jours, pour le mois
 *       en cours.</li>
 * </ol>
 * Tout le reste est ignoré ici (le suivi des tâches le traite de son côté) et n'est pas stocké. Un message n'est
 * traité qu'une fois (wamid unique). Le contenu d'un message n'est jamais cru pour une décision financière : « C'est
 * fait » relit toujours la base.
 */
@Service
public class KapsoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(KapsoWebhookService.class);

    private final ReminderResponseService responseService;
    private final ReminderCaseRepository caseRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final ReminderWhatsAppReplyRepository replyRepository;
    private final KapsoClient client;
    private final ReminderClock clock;

    public KapsoWebhookService(ReminderResponseService responseService, ReminderCaseRepository caseRepository,
                               ReminderDeliveryRepository deliveryRepository, ReminderWhatsAppReplyRepository replyRepository,
                               KapsoClient client, ReminderClock clock) {
        this.responseService = responseService;
        this.caseRepository = caseRepository;
        this.deliveryRepository = deliveryRepository;
        this.replyRepository = replyRepository;
        this.client = client;
        this.clock = clock;
    }

    /**
     * Une transaction par événement (chargement des relations du dossier) ; la réponse au membre part dans la même
     * requête, après le traitement de sa réponse.
     */
    @Transactional
    public void handle(String event, JsonNode payload) {
        JsonNode message = payload.path("message");
        if (message.isMissingNode()) {
            return;
        }
        String kind = event != null ? event : "";
        String direction = message.path("kapso").path("direction").asText("");
        if (kind.equals("whatsapp.message.received") || (kind.isEmpty() && direction.equals("inbound"))) {
            inbound(payload, message);
        } else if (kind.startsWith("whatsapp.message.") || direction.equals("outbound")) {
            status(message);
        }
    }

    // ------------------------------------------------------------------------------------------------ entrants

    private void inbound(JsonNode payload, JsonNode message) {
        String wamid = text(message.path("id"));
        String number = WhatsAppChannel.normalize(firstNonBlank(
            text(payload.path("conversation").path("phone_number")), text(message.path("from"))));
        if (wamid == null || number == null) {
            log.info("Relances : message WhatsApp entrant ignoré (numéro non reconnu)");
            return;
        }
        String buttonId = firstNonBlank(text(message.path("button").path("payload")),
            text(message.path("interactive").path("button_reply").path("id")),
            text(message.path("interactive").path("list_reply").path("id")));
        String body = firstNonBlank(text(message.path("button").path("text")),
            text(message.path("interactive").path("button_reply").path("title")),
            text(message.path("interactive").path("list_reply").path("title")),
            text(message.path("text").path("body")),
            text(message.path("kapso").path("content")));
        String contextId = text(message.path("context").path("id"));

        // Numéro partagé avec le suivi des tâches MLA : on ne traite que les boutons/listes « mlaf: » et les réponses
        // (context.id) à un message envoyé par MLA Finance. Tout le reste (FAIT|…, LISTE|…, RECU|…, textes libres
        // « Bien reçu », « c'est fait », MLA-AAAA-Snn-nn…) appartient au suivi des tâches : ignoré, sans réponse.
        Optional<ReminderPayloads.Parsed> parsed = ReminderPayloads.parse(buttonId);
        if (buttonId != null && parsed.isEmpty()) {
            return;
        }
        Optional<ReminderCase> target = parsed.isPresent()
            ? caseRepository.findById(parsed.get().caseId())
            : caseOfOurMessage(contextId);
        if (target.isEmpty()) {
            return;
        }
        if (!WhatsAppChannel.sameNumber(target.get().getDonor().getPhone(), number)) {
            log.info("Relances : réponse au dossier #{} ignorée (numéro différent de celui du membre)", target.get().getId());
            return;
        }
        ReminderCase rc = target.get();
        // Collecte : les dates écrites en texte libre se lisent dans le mois en cours.
        YearMonth month = rc.getPeriodMonth() != null ? YearMonth.from(rc.getPeriodMonth())
            : YearMonth.from(clock.now().atZone(ReminderClock.ZONE));

        ReminderResponseType type;
        LocalDate date = null;
        String reference = null;
        if (parsed.isPresent()) {
            ReminderPayloads.Parsed p = parsed.get();
            type = switch (p.action()) {
                case "ack" -> ReminderResponseType.ACK;
                case "done" -> ReminderResponseType.DONE;
                case "unsure" -> ReminderResponseType.UNSURE;
                default -> ReminderResponseType.PROMISE;
            };
            date = p.date();
        } else {
            ReminderResponseService.Classified c = ReminderResponseService.classify(body, month);
            type = c.type();
            date = c.date();
            reference = c.reference();
        }

        Optional<ReminderResponseService.Outcome> outcome =
            responseService.respond(rc.getId(), ReminderChannel.WHATSAPP, type, date, reference, body, wamid);
        outcome.ifPresent(o -> reply(number, rc.getId(), o));
    }

    /** Réponse au membre, hors transaction (le message reçu ouvre la fenêtre de 24 h : texte ou liste libres). */
    private void reply(String number, Long caseId, ReminderResponseService.Outcome outcome) {
        KapsoClient.Result result;
        if (outcome.asksForDate()) {
            List<KapsoClient.ListRow> rows = new ArrayList<>();
            outcome.dateChoices().forEach(c -> rows.add(new KapsoClient.ListRow(
                ReminderPayloads.date(caseId, c.date()), c.title(), c.description())));
            rows.add(new KapsoClient.ListRow(ReminderPayloads.unsure(caseId), "Je ne sais pas encore", null));
            result = client.sendList(number, outcome.reply(), "Choisir une date", rows);
        } else if (outcome.reply() != null) {
            // Une réponse tapée sans « Répondre » n'est pas reconnue (numéro partagé) : on le précise quand on pose une question.
            String text = outcome.reply().trim().endsWith("?")
                ? outcome.reply() + "\n\n↩️ Répondez directement à ce message (appui long puis « Répondre »)." : outcome.reply();
            result = client.sendText(number, text);
        } else {
            return;
        }
        if (!result.sent()) {
            log.warn("Relances : réponse WhatsApp au dossier #{} non envoyée ({})", caseId, result.error());
        } else if (result.messageId() != null) {
            replyRepository.save(ReminderWhatsAppReply.builder().reminderCase(caseRepository.getReferenceById(caseId))
                .providerMessageId(result.messageId()).createdAt(clock.now()).build());
        }
    }

    /** Dossier du message MLA Finance auquel le membre répond (relance ou réponse déjà envoyée) ; vide sinon. */
    private Optional<ReminderCase> caseOfOurMessage(String contextId) {
        if (contextId == null) {
            return Optional.empty();
        }
        return deliveryRepository.findFirstByProviderMessageId(contextId).map(d -> d.getReminder().getReminderCase())
            .or(() -> replyRepository.findFirstByProviderMessageId(contextId).map(ReminderWhatsAppReply::getReminderCase));
    }

    // ------------------------------------------------------------------------------------------------ statuts

    /** sent / delivered / read / failed d'un message envoyé par MLA Finance ; les autres numéros sont ignorés. */
    private void status(JsonNode message) {
        String wamid = text(message.path("id"));
        if (wamid == null) {
            return;
        }
        Optional<ReminderDelivery> found = deliveryRepository.findFirstByProviderMessageId(wamid);
        if (found.isEmpty()) {
            return;
        }
        ReminderDelivery delivery = found.get();
        String status = message.path("kapso").path("status").asText("");
        ReminderDeliveryStatus next = switch (status) {
            case "sent" -> ReminderDeliveryStatus.SENT;
            case "delivered" -> ReminderDeliveryStatus.DELIVERED;
            case "read" -> ReminderDeliveryStatus.READ;
            case "failed" -> ReminderDeliveryStatus.FAILED;
            default -> null;
        };
        if (next == null || rank(next) < rank(delivery.getStatus())) {
            return;
        }
        delivery.setStatus(next);
        if (next == ReminderDeliveryStatus.FAILED) {
            delivery.setError(failure(message));
        }
        deliveryRepository.save(delivery);
    }

    /** Un statut ne recule jamais (un « delivered » en retard n'efface pas un « read ») ; l'échec n'écrase pas une livraison. */
    private static int rank(ReminderDeliveryStatus s) {
        return switch (s) {
            case QUEUED -> 0;
            case SENT -> 1;
            case FAILED -> 2;
            case DELIVERED -> 3;
            case READ -> 4;
        };
    }

    private static String failure(JsonNode message) {
        for (JsonNode st : message.path("kapso").path("statuses")) {
            JsonNode err = st.path("errors").path(0);
            if (!err.isMissingNode()) {
                String s = err.path("code").asText("") + " " + err.path("title").asText("") + " — " + err.path("message").asText("");
                return s.length() > 300 ? s.substring(0, 299) + "…" : s.trim();
            }
        }
        return "Échec de livraison WhatsApp";
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
