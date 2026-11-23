package org.mla.finance.reminder;

/**
 * Ce qu'il faut pour rendre les boutons d'une relance : le dossier (identifiants « mlaf: » des boutons WhatsApp) et les
 * jetons des liens. Pour un aperçu, des valeurs factices (aucun jeton n'est émis).
 */
public record ReminderLinks(Long caseId, String actionToken, String contributeToken) {

    public static ReminderLinks preview() {
        return new ReminderLinks(0L, "apercu", "apercu");
    }
}
