package org.mla.finance.payment;

import org.mla.finance.domain.PaymentTransactionStatus;

/**
 * Résultat de l'initiation d'un paiement auprès d'un PaymentProvider : le statut initial à donner
 * à la PaymentTransaction, la référence côté prestataire si elle existe, et le motif d'échec si le
 * prestataire a rejeté la demande d'emblée.
 * <ul>
 *   <li>{@link #manual()} : PENDING, confirmation humaine attendue (ManualPaymentProvider).</li>
 *   <li>{@link #processing(String)} : PROCESSING, le prestataire résoudra le paiement plus tard
 *       (callback ou polling) — y compris quand la réponse du prestataire est inconnue (coupure
 *       réseau, 500) : on ne suppose jamais un échec sans l'avoir lu chez lui.</li>
 *   <li>{@link #rejected(String, String)} : FAILED immédiat, rejet explicite du prestataire.</li>
 * </ul>
 */
public record PaymentInitiationResult(
    PaymentTransactionStatus status,
    String providerReference,
    String providerStatus,
    String failureCode,
    String failureMessage
) {
    public static PaymentInitiationResult manual() {
        return new PaymentInitiationResult(PaymentTransactionStatus.PENDING, null, null, null, null);
    }

    public static PaymentInitiationResult processing(String providerReference, String providerStatus) {
        return new PaymentInitiationResult(PaymentTransactionStatus.PROCESSING, providerReference, providerStatus, null, null);
    }

    public static PaymentInitiationResult rejected(String failureCode, String failureMessage) {
        return new PaymentInitiationResult(PaymentTransactionStatus.FAILED, null, "REJECTED", failureCode, failureMessage);
    }
}
