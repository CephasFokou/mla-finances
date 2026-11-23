package org.mla.finance.domain;

public enum AccessTokenPurpose {
    /** Page de réponse d'un email de relance (« Bien reçu », date, « C'est déjà fait »). */
    REMINDER_ACTION,
    /** Bouton « Contribuer maintenant » : ouvre la contribution mensuelle, membre et mois présélectionnés. */
    CONTRIBUTE
}
