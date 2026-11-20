package org.mla.finance.payment.pawapay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ligne du grand livre du projet MLA chez wallet-ledger (GET /api/v1/movements). Montant signé : une
 * sortie (payout, frais) est négative. type : DEPOSIT, FEE, PAYOUT, REFUND… ; status : PENDING,
 * COMPLETED, FAILED. Un frais porte l'identifiant de l'opération qui l'a causé (depositId ou payoutId)
 * et sa clientReferenceId quand wallet-ledger l'a rattaché à une opération connue.
 */
public record LedgerMovement(Long id, String type, String status, BigDecimal amount, String depositId, String payoutId,
                             String refundId, String clientReferenceId, String description, Instant occurredAt) {

    public static final String FEE = "FEE";
    public static final String COMPLETED = "COMPLETED";

    public boolean isCompletedFee() {
        return FEE.equals(type) && COMPLETED.equals(status);
    }

    /** Une page de GET /api/v1/movements (tri : plus récent d'abord). */
    public record Page(List<LedgerMovement> content, int page, int totalPages) {
    }
}
