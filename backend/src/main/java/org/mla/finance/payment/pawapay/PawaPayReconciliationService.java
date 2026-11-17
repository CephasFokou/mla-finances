package org.mla.finance.payment.pawapay;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.service.PaymentTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Réconciliation d'un don en ligne : relit le statut du dépôt chez PawaPay puis l'applique via
 * PaymentTransactionService.applyProviderOutcome (sous verrou, idempotent). Point d'entrée unique
 * pour les trois déclencheurs — callback PawaPay, job de polling, bouton « Vérifier le statut » du
 * back-office — de sorte qu'aucun d'eux ne fasse confiance à autre chose qu'à une lecture directe
 * chez PawaPay avec notre jeton API. Un callback forgé ou rejoué ne peut donc au pire que
 * provoquer une relecture (et il est de toute façon limité en débit et protégé par le secret
 * d'URL, voir PawaPayWebhookController).
 * <p>
 * L'appel réseau se fait hors transaction et hors verrou (il peut durer jusqu'au read-timeout) ;
 * seul l'état déjà connu est consulté avant, pour ne pas appeler PawaPay inutilement sur un don
 * déjà final.
 */
@Service
@RequiredArgsConstructor
public class PawaPayReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PawaPayReconciliationService.class);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final PawaPayClient client;

    public Optional<PaymentTransaction> reconcile(Long transactionId) {
        Optional<PaymentTransaction> current = paymentTransactionRepository.findById(transactionId);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        PaymentTransaction tx = current.get();
        if (!tx.isOnline() || tx.getStatus().isFinal()) {
            return current;
        }
        DepositOutcome outcome = client.checkDeposit(tx.getDepositId());
        log.debug("Réconciliation don {} depositId={} : {} ({})", tx.getReference(), tx.getDepositId(), outcome.kind(), outcome.providerStatus());
        return paymentTransactionService.applyProviderOutcome(transactionId, outcome);
    }

    public Optional<PaymentTransaction> reconcileByDepositId(String depositId) {
        return paymentTransactionRepository.findByDepositId(depositId).flatMap(tx -> reconcile(tx.getId()));
    }
}
