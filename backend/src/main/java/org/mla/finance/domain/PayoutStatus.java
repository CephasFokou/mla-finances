package org.mla.finance.domain;

/**
 * État du décaissement Mobile Money d'un retrait (payout wallet-ledger), distinct de
 * {@link WithdrawalStatus} : le retrait reste APPROVED tant que le payout n'est pas COMPLETED.
 * PROCESSING et PENDING_APPROVAL sont « actifs » (annulation et décaissement manuel bloqués) ;
 * COMPLETED et FAILED sont finaux pour ce payoutId (après FAILED, une nouvelle tentative crée un
 * nouveau payoutId).
 */
public enum PayoutStatus {
    /** Envoyé (ou statut encore inconnu après une coupure) : la relecture tranchera. */
    PROCESSING,
    /** Au-dessus du seuil wallet-ledger : attend une validation dans la Trésorerie. */
    PENDING_APPROVAL,
    COMPLETED,
    FAILED;

    public boolean isActive() {
        return this == PROCESSING || this == PENDING_APPROVAL;
    }
}
