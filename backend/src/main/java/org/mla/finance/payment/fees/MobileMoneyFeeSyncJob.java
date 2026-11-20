package org.mla.finance.payment.fees;

import lombok.RequiredArgsConstructor;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passage quotidien de MobileMoneyFeeSyncService, par défaut à 07:00 UTC : après l'import des relevés
 * PawaPay par wallet-ledger (05:30 UTC) et son contrôle de cohérence (06:00 UTC). Interrupteur
 * MLA_FEES_SYNC_ENABLED (true par défaut si PAWAPAY_ENABLED=true).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.payment.fees", name = "sync-enabled", havingValue = "true")
@RequiredArgsConstructor
public class MobileMoneyFeeSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MobileMoneyFeeSyncJob.class);

    private final MobileMoneyFeeSyncService syncService;
    private final PawaPayProperties pawaPayProperties;

    @Scheduled(cron = "${app.payment.fees.sync-cron:0 0 7 * * *}", zone = "UTC")
    public void run() {
        if (!pawaPayProperties.isEnabled()) {
            log.info("Frais Mobile Money : PawaPay désactivé (mode manuel), rien à synchroniser");
            return;
        }
        try {
            syncService.sync();
        } catch (RuntimeException e) {
            // Contrainte d'unicité (deux instances en même temps) ou donnée inattendue : rien n'est compté
            // à moitié (une seule transaction), le passage suivant reprend.
            log.error("Frais Mobile Money : passage interrompu ({})", e.getMessage(), e);
        }
    }
}
