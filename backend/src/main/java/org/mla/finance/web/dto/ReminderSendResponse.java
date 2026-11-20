package org.mla.finance.web.dto;

import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.domain.ReminderDeliveryStatus;

import java.time.Instant;
import java.util.List;

/**
 * Résultat d'une relance. {@code queued} : demandée hors plage horaire, partira à {@code scheduledFor}.
 * {@code sent=false} et non mise en file : aucun canal n'a abouti (voir {@code deliveries}) — la tentative est
 * tracée mais ne compte pas dans le maximum et ne déclenche pas de délai d'attente.
 */
public record ReminderSendResponse(
    Long caseId,
    int sequenceNo,
    boolean sent,
    boolean queued,
    Instant scheduledFor,
    Instant nextAllowedAt,
    List<Delivery> deliveries
) {
    public record Delivery(ReminderChannel channel, String recipient, ReminderDeliveryStatus status, String error) {
    }
}
