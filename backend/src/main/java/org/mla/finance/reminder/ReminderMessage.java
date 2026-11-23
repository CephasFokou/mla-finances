package org.mla.finance.reminder;

import org.mla.finance.reminder.whatsapp.WhatsAppTemplate;

/**
 * Message de relance rendu : objet et corps email (HTML + texte), modèle WhatsApp avec ses paramètres. Le texte est
 * repris dans l'historique du dossier.
 */
public record ReminderMessage(String subject, String html, String text, WhatsAppTemplate whatsapp) {
}
