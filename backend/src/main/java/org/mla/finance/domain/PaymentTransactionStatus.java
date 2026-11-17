package org.mla.finance.domain;

/**
 * Cycle de vie d'une intention de don reçue via le front-office public (voir PaymentTransaction).
 * <ul>
 *   <li>PENDING : mode manuel — en attente de la confirmation du secrétariat.</li>
 *   <li>PROCESSING : mode en ligne — demande de paiement acceptée par le prestataire (PawaPay), en
 *       attente de la validation du donateur sur son téléphone ; résolu par callback ou polling.</li>
 *   <li>CONFIRMED : paiement reçu, Contribution créée (final).</li>
 *   <li>FAILED : le prestataire a déclaré le paiement échoué (final, aucune Contribution).</li>
 *   <li>CANCELLED : annulé par le secrétariat (final, aucune Contribution).</li>
 * </ul>
 */
public enum PaymentTransactionStatus {
    PENDING,
    PROCESSING,
    CONFIRMED,
    FAILED,
    CANCELLED;

    /** Un statut final n'évolue plus jamais — garde-fou d'idempotence (RG-20). */
    public boolean isFinal() {
        return this == CONFIRMED || this == FAILED || this == CANCELLED;
    }
}
