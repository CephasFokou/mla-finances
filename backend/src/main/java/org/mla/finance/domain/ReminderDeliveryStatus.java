package org.mla.finance.domain;

/** Statut d'un envoi sur un canal ; DELIVERED / READ ne sont connus que par WhatsApp (webhook Kapso, lot 2). */
public enum ReminderDeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
