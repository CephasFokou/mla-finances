package org.mla.finance.web.dto;

import org.mla.finance.domain.ReminderChannel;

import java.util.List;

/** Aperçu exact avant envoi (§5.3) : objet, corps, et canaux / destinataires (masqués) qui seraient utilisés. */
public record ReminderPreviewResponse(String subject, String html, String text, String whatsappText, List<Target> channels,
                                      boolean canRemind, boolean canForce, String blockedReason) {

    public record Target(ReminderChannel channel, String recipient) {
    }
}
