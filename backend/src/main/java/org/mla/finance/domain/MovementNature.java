package org.mla.finance.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nature d'un mouvement, reprise telle quelle de la colonne « Nature » du fichier de suivi
 * (MLA-ICC-YDE-FINANCE-2026). Une entrée est une {@link Contribution}, une sortie un
 * {@link Withdrawal} ; TRANSFER et REGULARIZATION existent dans les deux sens.
 */
public enum MovementNature {
    MONTHLY_CONTRIBUTION("Contribution mensuelle", true, false),
    CONTRIBUTION("Contribution", true, false),
    DONATION("Don", true, false),
    RETURN("Retour", true, false),
    ADVANCE("Avance", true, false),
    EXPENSE("Dépense", false, true),
    HANDOVER("Remise", false, true),
    CASH_TOP_UP("Complément de la caisse", false, true),
    REPAYMENT("Remboursement", false, true),
    FEE("Frais", false, true),
    TRANSFER("Transfert", true, true),
    REGULARIZATION("Régularisation", true, true);

    /** Natures d'une entrée qui comptent comme « collecté » pour une collecte (onglet Collectes). */
    public static final Set<MovementNature> COLLECTED = EnumSet.of(CONTRIBUTION, DONATION, MONTHLY_CONTRIBUTION);

    private final String label;
    private final boolean inflow;
    private final boolean outflow;

    MovementNature(String label, boolean inflow, boolean outflow) {
        this.label = label;
        this.inflow = inflow;
        this.outflow = outflow;
    }

    public String label() {
        return label;
    }

    public boolean isInflow() {
        return inflow;
    }

    public boolean isOutflow() {
        return outflow;
    }
}
