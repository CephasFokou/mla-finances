package org.mla.finance.domain;

/**
 * État d'un dossier de relance (cadrage Relances §3). Le lot 1 utilise A_RELANCER (dossier dont aucun envoi n'a
 * abouti), EN_ATTENTE_REPONSE, REGLE et SANS_SUITE ; ACCUSE, PROMIS, VERIFICATION et DECLARE_NON_RETROUVE viennent
 * des réponses du membre (lot 2). Un membre sans dossier est lui aussi « à relancer ».
 */
public enum ReminderCaseStatus {
    A_RELANCER,
    EN_ATTENTE_REPONSE,
    ACCUSE,
    PROMIS,
    VERIFICATION,
    DECLARE_NON_RETROUVE,
    REGLE,
    SANS_SUITE;

    public boolean isClosed() {
        return this == REGLE || this == SANS_SUITE;
    }
}
