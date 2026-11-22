package org.mla.finance.domain;

/** Origine d'une relance : bouton « Relancer », relance groupée, ou rappel programmé (date promise, lot 2). */
public enum ReminderTrigger {
    MANUAL,
    BULK,
    SCHEDULED
}
