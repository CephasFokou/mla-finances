package org.mla.finance.domain;

/** Cycle de vie d'une intention de don reçue via le front-office public (voir PaymentTransaction). */
public enum PaymentTransactionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
