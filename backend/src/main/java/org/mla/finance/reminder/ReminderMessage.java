package org.mla.finance.reminder;

/** Message de relance rendu : objet et corps email (HTML + texte), texte repris dans l'historique du dossier. */
public record ReminderMessage(String subject, String html, String text) {
}
