package org.mla.finance.domain;

/** Réponse d'un membre à une relance (cadrage Relances §4). */
public enum ReminderResponseType {
    /** « Bien reçu », sans date pour l'instant. */
    ACK,
    /** Date de versement choisie. */
    PROMISE,
    /** « Je ne sais pas encore ». */
    UNSURE,
    /** « C'est déjà fait » : vérifié en base, jamais cru sur parole. */
    DONE,
    /** Référence de paiement MLA-… envoyée par le membre. */
    REFERENCE,
    /** Texte non reconnu : à lire par un trésorier. */
    FREE_TEXT
}
