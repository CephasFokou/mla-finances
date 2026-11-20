package org.mla.finance.payment.fees;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** app.payment.fees : synchronisation quotidienne des frais Mobile Money depuis wallet-ledger. */
@Component
@ConfigurationProperties(prefix = "app.payment.fees")
@Getter
@Setter
public class MobileMoneyFeesProperties {

    /** MLA_FEES_SYNC_ENABLED, true par défaut quand PAWAPAY_ENABLED=true. */
    private boolean syncEnabled = false;
    private String syncCron = "0 0 7 * * *";
    private int lookbackDays = 31;
    /** Début du contrôle d'écart si wallet-ledger ne renvoie pas de date d'ouverture. */
    private Instant ledgerStart;
}
