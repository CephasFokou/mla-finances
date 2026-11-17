package org.mla.finance.payment.pawapay;

/**
 * Lecture normalisée de l'état d'un dépôt chez PawaPay (réponse d'initiation ou de relecture),
 * indépendante du format exact de l'API — c'est ce que PaymentTransactionService.applyProviderOutcome
 * sait appliquer.
 */
public record DepositOutcome(
    Kind kind,
    String providerStatus,
    String providerTransactionId,
    String failureCode,
    String failureMessage
) {
    public enum Kind {
        /** Fonds reçus sur le portefeuille PawaPay : final. */
        COMPLETED,
        /** PawaPay a déclaré l'échec (ou le rejet à l'initiation) : final. */
        FAILED,
        /** Le depositId est inconnu de PawaPay : la demande n'y est jamais arrivée, donc échec (final). */
        NOT_FOUND,
        /** ACCEPTED / PROCESSING / IN_RECONCILIATION : relire plus tard. */
        PENDING,
        /** PawaPay injoignable ou réponse inexploitable : rien à conclure, relire plus tard. */
        UNAVAILABLE
    }

    public static DepositOutcome completed(String providerStatus, String providerTransactionId) {
        return new DepositOutcome(Kind.COMPLETED, providerStatus, providerTransactionId, null, null);
    }

    public static DepositOutcome failed(String providerStatus, String failureCode, String failureMessage) {
        return new DepositOutcome(Kind.FAILED, providerStatus, null, failureCode, failureMessage);
    }

    public static DepositOutcome notFound() {
        return new DepositOutcome(Kind.NOT_FOUND, "NOT_FOUND", null, "DEPOSIT_NOT_FOUND",
            "La demande de paiement n'a jamais été reçue par PawaPay.");
    }

    public static DepositOutcome pending(String providerStatus) {
        return new DepositOutcome(Kind.PENDING, providerStatus, null, null, null);
    }

    public static DepositOutcome unavailable(String detail) {
        return new DepositOutcome(Kind.UNAVAILABLE, null, null, null, detail);
    }

    public boolean isFinal() {
        return kind == Kind.COMPLETED || kind == Kind.FAILED || kind == Kind.NOT_FOUND;
    }
}
