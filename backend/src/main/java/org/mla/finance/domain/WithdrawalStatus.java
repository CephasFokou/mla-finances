package org.mla.finance.domain;

/**
 * Cycle de vie d'une demande de retrait : PENDING -> APPROVED|REJECTED ; APPROVED -> PROCESSED
 * (décaissement effectif) ; PENDING ou APPROVED -> CANCELLED. Aucune transition arrière — à
 * confirmer avec Cephas si ce séquencement ne correspond pas au workflow réel attendu.
 */
public enum WithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PROCESSED,
    CANCELLED
}
