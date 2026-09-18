package org.mla.finance.domain;

/** RG : une contribution est STANDARD, ou issue d'un TRANSFER entre enveloppes, ou d'un ADJUSTMENT tracé. */
public enum MovementType {
    STANDARD,
    TRANSFER,
    ADJUSTMENT
}
