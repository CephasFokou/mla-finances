package org.mla.finance.payment.pawapay;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;

/**
 * Relecture périodique des dons PROCESSING dont la prochaine vérification est due. C'est ce qui
 * résout les paiements en local (PawaPay ne peut pas joindre un poste de développement pour
 * livrer son callback) et, en production, ce qui rattrape tout callback perdu. Le recul
 * exponentiel et l'arrêt après le délai de réconciliation sont décidés par
 * PaymentTransactionService.scheduleNextCheck. Activé uniquement avec PawaPay ; une seule instance
 * de l'API tourne (Railway), donc pas de verrou distribué — si un jour plusieurs instances
 * cohabitent, le verrou de ligne pris à l'application rend de toute façon le traitement
 * concurrent sûr (au prix d'appels PawaPay en double).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.payment.pawapay", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PawaPayPollingJob {

    private static final Logger log = LoggerFactory.getLogger(PawaPayPollingJob.class);
    private static final int BATCH_SIZE = 50;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PawaPayReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${app.payment.pawapay.poll-interval-ms:15000}", initialDelayString = "${app.payment.pawapay.poll-initial-delay-ms:10000}")
    public void pollPendingDeposits() {
        List<PaymentTransaction> due = paymentTransactionRepository
            .findByStatusAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(PaymentTransactionStatus.PROCESSING, Instant.now(),
                PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.debug("Réconciliation PawaPay : {} don(s) à relire", due.size());
        for (PaymentTransaction tx : due) {
            try {
                reconciliationService.reconcile(tx.getId());
            } catch (RuntimeException e) {
                // Un don en erreur ne doit pas bloquer les autres ; il sera repris au prochain tour.
                log.error("Réconciliation du don {} échouée", tx.getReference(), e);
            }
        }
    }
}
