package org.mla.finance.payment;

import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;

/**
 * Abstraction du prestataire de paiement, sur le même principe que MediaStorageService pour le
 * stockage média. Deux implémentations : ManualPaymentProvider (aucun appel externe, le
 * secrétariat confirme à réception des fonds) et PawaPayProvider (Orange Money / MTN MoMo via
 * l'API PawaPay, confirmation automatique par réconciliation). PaymentProviderResolver choisit
 * l'implémentation en fonction du canal de paiement : seuls les canaux déclarés dans
 * app.payment.pawapay.providers passent en ligne, les autres (carte, espèces, virement) restent
 * en mode manuel. PublicDonationService et les contrôleurs ne connaissent que cette interface.
 */
public interface PaymentProvider {

    String code();

    /** Ce prestataire sait-il traiter un don sur ce canal ? */
    boolean supports(PaymentChannel channel);

    /**
     * Initie le paiement pour une transaction déjà construite (montant, canal, depositId...). Ne
     * doit jamais lever d'exception pour un problème côté prestataire : renvoyer
     * {@link PaymentInitiationResult#processing} et laisser la réconciliation trancher.
     */
    PaymentInitiationResult initiate(PaymentTransaction transaction);
}
