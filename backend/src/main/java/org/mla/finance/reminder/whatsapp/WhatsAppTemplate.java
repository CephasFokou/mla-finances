package org.mla.finance.reminder.whatsapp;

import java.util.List;
import java.util.Map;

/**
 * Modèle WhatsApp approuvé à envoyer : nom, paramètres nommés du corps (ordre conservé), identifiants des boutons de
 * réponse rapide (« mlaf:… », dans l'ordre des boutons) et suffixe du bouton lien (jeton signé), placé après eux.
 */
public record WhatsAppTemplate(String name, Map<String, String> bodyParams, List<String> quickReplyPayloads,
                               String urlSuffix) {
}
