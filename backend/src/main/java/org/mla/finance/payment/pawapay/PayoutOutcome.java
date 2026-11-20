package org.mla.finance.payment.pawapay;

/**
 * Lecture normalisée d'un payout chez wallet-ledger (réponse à POST /v2/payouts ou relecture
 * GET /v2/payouts/{payoutId}), indépendante du format exact — c'est ce que
 * WithdrawalPayoutStateService.apply sait appliquer.
 */
public record PayoutOutcome(Kind kind, String providerStatus, String failureCode, String failureMessage) {

    public enum Kind {
        /** ACCEPTED / DUPLICATE_IGNORED / ENQUEUED / PROCESSING / IN_RECONCILIATION : relire plus tard. */
        PROCESSING,
        /** Au-dessus du seuil du projet : attend une validation dans la Trésorerie wallet-ledger. */
        PENDING_APPROVAL,
        /** Argent envoyé au destinataire : final. */
        COMPLETED,
        /** Refusé (règle wallet-ledger, opérateur) ou échoué : final. */
        FAILED,
        /** payoutId inconnu de wallet-ledger : la demande n'y est jamais arrivée. */
        NOT_FOUND,
        /** wallet-ledger injoignable, 5xx ou réponse inexploitable : rien à conclure, relire plus tard. */
        UNAVAILABLE
    }

    public static PayoutOutcome processing(String providerStatus) {
        return new PayoutOutcome(Kind.PROCESSING, providerStatus, null, null);
    }

    public static PayoutOutcome pendingApproval() {
        return new PayoutOutcome(Kind.PENDING_APPROVAL, "PENDING_APPROVAL", null, null);
    }

    public static PayoutOutcome completed() {
        return new PayoutOutcome(Kind.COMPLETED, "COMPLETED", null, null);
    }

    public static PayoutOutcome failed(String providerStatus, String failureCode, String failureMessage) {
        return new PayoutOutcome(Kind.FAILED, providerStatus, failureCode != null ? failureCode : "REJECTED", failureMessage);
    }

    public static PayoutOutcome notFound() {
        return new PayoutOutcome(Kind.NOT_FOUND, "NOT_FOUND", "PAYOUT_NOT_FOUND", "Demande jamais reçue par wallet-ledger.");
    }

    public static PayoutOutcome unavailable(String detail) {
        return new PayoutOutcome(Kind.UNAVAILABLE, null, null, detail);
    }
}
