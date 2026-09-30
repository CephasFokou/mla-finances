package org.mla.finance.payment;

/**
 * Résultat de l'initiation d'un paiement auprès d'un PaymentProvider. requiresManualConfirmation
 * indique si l'intention de don doit rester PENDING en attendant une confirmation humaine
 * (ManualPaymentProvider : toujours true aujourd'hui) ou si le prestataire confirmera lui-même le
 * paiement plus tard via un mécanisme asynchrone (ex. webhook PawaPay).
 */
public record PaymentInitiationResult(boolean requiresManualConfirmation, String providerReference) {
}
