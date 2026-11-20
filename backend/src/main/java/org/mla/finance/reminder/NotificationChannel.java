package org.mla.finance.reminder;

import org.mla.finance.domain.Donor;
import org.mla.finance.domain.ReminderChannel;

import java.util.Optional;

/**
 * Canal d'envoi d'une relance (RL-03). Email (Resend) au lot 1 ; WhatsApp (Kapso) au lot 2. Un canal ne lève
 * jamais d'exception : l'échec est renvoyé et tracé, l'autre canal part quand même.
 */
public interface NotificationChannel {

    ReminderChannel channel();

    /** Canal configuré et actif (ex. KAPSO_ENABLED). Un canal inactif n'est pas proposé. */
    boolean isEnabled();

    /** Adresse ou numéro utilisable pour ce membre, vide si le membre n'est pas joignable par ce canal. */
    Optional<String> recipientFor(Donor donor);

    Outcome send(String recipient, ReminderMessage message);

    record Outcome(boolean sent, String providerMessageId, String error) {
    }
}
