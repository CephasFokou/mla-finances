package org.mla.finance.payment;

import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.springframework.stereotype.Component;

/**
 * Mode « manuel » : n'appelle aucun service externe, ne fait aucun mouvement d'argent réel. Chaque
 * don initié via ce provider reste PENDING jusqu'à ce qu'un membre du staff le confirme depuis le
 * back-office, après avoir vérifié la réception réelle du paiement (relevé Mobile Money, espèces
 * remises au secrétariat, virement...) — voir PaymentTransactionService.confirm(...). Accepte
 * tous les canaux : c'est le repli de PaymentProviderResolver quand aucun prestataire en ligne ne
 * prend le canal en charge (ou que PawaPay est désactivé).
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    public static final String CODE = "MANUAL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean supports(PaymentChannel channel) {
        return true;
    }

    @Override
    public PaymentInitiationResult initiate(PaymentTransaction transaction) {
        return PaymentInitiationResult.manual();
    }
}
