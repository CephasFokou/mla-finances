package org.mla.finance.payment;

import org.mla.finance.domain.PaymentTransaction;
import org.springframework.stereotype.Component;

/**
 * Seule implémentation de PaymentProvider disponible pour l'instant (voir la javadoc de
 * PaymentProvider) : n'appelle aucun service externe, ne fait aucun mouvement d'argent réel.
 * Chaque don public initié via ce provider reste PENDING jusqu'à ce qu'un membre du staff le
 * confirme manuellement depuis le back-office, après avoir vérifié la réception réelle du
 * paiement (relevé Mobile Money, espèces remises au secrétariat, etc.) — voir
 * PaymentTransactionService.confirm(...).
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    public static final String CODE = "MANUAL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public PaymentInitiationResult initiate(PaymentTransaction transaction) {
        return new PaymentInitiationResult(true, null);
    }
}
