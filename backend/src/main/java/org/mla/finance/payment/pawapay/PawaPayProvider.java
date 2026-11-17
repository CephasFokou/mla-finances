package org.mla.finance.payment.pawapay;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.payment.PaymentInitiationResult;
import org.mla.finance.payment.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Prestataire PawaPay (API v2, Orange Money / MTN MoMo Cameroun). N'existe comme bean que si
 * app.payment.pawapay.enabled=true : sinon PaymentProviderResolver ne voit que le mode manuel et
 * rien ne change pour l'application. Les validations métier (numéro, bornes, anti-abus) sont
 * faites avant, dans PublicDonationService ; ici on ne fait que l'appel et sa traduction.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment.pawapay", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PawaPayProvider implements PaymentProvider {

    public static final String CODE = "PAWAPAY";
    private static final Logger log = LoggerFactory.getLogger(PawaPayProvider.class);

    private final PawaPayClient client;
    private final PawaPayProperties properties;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean supports(PaymentChannel channel) {
        return channel != null && properties.getProviders().containsKey(channel.getCode());
    }

    @Override
    public PaymentInitiationResult initiate(PaymentTransaction tx) {
        String provider = properties.getProviders().get(tx.getPaymentChannel().getCode());
        // XAF : pas de décimales chez PawaPay — le montant a déjà été validé entier en amont.
        String amount = tx.getAmount().stripTrailingZeros().toPlainString();
        // 4 à 22 caractères alphanumériques + espaces : "MLA DON 123456" (libellé vu par le donateur).
        String customerMessage = customerMessage(tx.getReference());

        log.info("PawaPay initiation depositId={} ref={} montant={} {} payeur={} opérateur={}",
            tx.getDepositId(), tx.getReference(), amount, properties.getCurrency(), PawaPayClient.maskPhone(tx.getPayerPhone()), provider);

        DepositOutcome outcome = client.initiateDeposit(tx.getDepositId(), amount, tx.getPayerPhone(), provider,
            tx.getReference(), customerMessage);

        return switch (outcome.kind()) {
            case FAILED -> PaymentInitiationResult.rejected(outcome.failureCode(), outcome.failureMessage());
            case PENDING -> PaymentInitiationResult.processing(tx.getDepositId(), outcome.providerStatus());
            // Réponse inconnue (500, coupure) : PawaPay a peut-être accepté la demande — on garde
            // PROCESSING et la réconciliation tranchera (NOT_FOUND => FAILED).
            default -> PaymentInitiationResult.processing(tx.getDepositId(), "UNKNOWN");
        };
    }

    /** "MLA DON 123456" pour MLA-123456 : alphanumérique + espaces, 4 à 22 caractères. */
    static String customerMessage(String reference) {
        String digits = reference == null ? "" : reference.replaceAll("[^A-Za-z0-9]", "");
        String message = ("MLA DON " + digits).trim();
        return message.length() > 22 ? message.substring(0, 22).trim() : message;
    }
}
