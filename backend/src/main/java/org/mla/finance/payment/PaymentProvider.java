package org.mla.finance.payment;

import org.mla.finance.domain.PaymentTransaction;

/**
 * Abstraction du prestataire de paiement, sur le même principe que MediaStorageService pour le
 * stockage média (voir sa javadoc) : le Cahier des Charges prévoit PawaPay (Orange Money, MTN
 * MoMo, carte), mais le compte marchand PawaPay n'est pas encore confirmé et aucune dépendance
 * PawaPay n'existe dans le projet. Cette interface est injectée dans PublicDonationService — la
 * seule implémentation actuelle (ManualPaymentProvider) n'appelle aucun service externe et laisse
 * chaque don en attente de confirmation manuelle par le staff. Le jour où PawaPay sera confirmé,
 * une nouvelle implémentation (ex. PawaPayPaymentProvider) suffira à initier de vrais paiements et
 * à les confirmer automatiquement via webhook (RG-20, idempotence), sans changer
 * PublicDonationService ni les contrôleurs publics.
 */
public interface PaymentProvider {

    String code();

    PaymentInitiationResult initiate(PaymentTransaction transaction);
}
