package org.mla.finance.payment;

import org.mla.finance.domain.PaymentChannel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Choisit le PaymentProvider d'un canal : le premier prestataire « en ligne » qui le prend en
 * charge, sinon le mode manuel. L'ordre des beans n'a pas d'importance : le manuel est toujours
 * considéré en dernier puisqu'il accepte tout.
 */
@Component
public class PaymentProviderResolver {

    private final List<PaymentProvider> onlineProviders;
    private final PaymentProvider manualProvider;

    public PaymentProviderResolver(List<PaymentProvider> providers) {
        this.onlineProviders = providers.stream().filter(p -> !ManualPaymentProvider.CODE.equals(p.code())).toList();
        this.manualProvider = providers.stream().filter(p -> ManualPaymentProvider.CODE.equals(p.code())).findFirst()
            .orElseThrow(() -> new IllegalStateException("ManualPaymentProvider manquant"));
    }

    public PaymentProvider resolve(PaymentChannel channel) {
        return onlineProviders.stream().filter(p -> p.supports(channel)).findFirst().orElse(manualProvider);
    }

    /** Un don sur ce canal sera-t-il payé en ligne (demande de paiement poussée sur le téléphone) ? */
    public boolean isOnline(PaymentChannel channel) {
        return onlineProviders.stream().anyMatch(p -> p.supports(channel));
    }
}
