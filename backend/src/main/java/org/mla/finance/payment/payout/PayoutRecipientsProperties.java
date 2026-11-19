package org.mla.finance.payment.payout;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liste fermée des numéros MLA vers lesquels un retrait peut être décaissé par Mobile Money
 * (app.payment.payout-recipients). Le back-office choisit dans cette liste, jamais de saisie libre.
 * C'est le miroir de la liste blanche du projet MLA dans wallet-ledger, qui reste l'autorité : un
 * numéro ajouté ici mais pas là-bas est refusé (RECIPIENT_NOT_ALLOWED). Configuration invalide =>
 * échec au démarrage plutôt qu'un payout vers un numéro mal saisi.
 */
@Component
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PayoutRecipientsProperties {

    private static final Pattern MSISDN = Pattern.compile("^2376\\d{8}$");
    private static final Set<String> PROVIDERS = Set.of("ORANGE_CMR", "MTN_MOMO_CMR");

    private List<Recipient> payoutRecipients = new ArrayList<>();

    @PostConstruct
    void validate() {
        Set<String> keys = new HashSet<>();
        for (Recipient r : payoutRecipients) {
            if (r.getMsisdn() == null || !MSISDN.matcher(r.getMsisdn()).matches()) {
                throw new IllegalStateException("app.payment.payout-recipients : MSISDN invalide (237 + 9 chiffres attendus) pour « "
                    + r.getLabel() + " »");
            }
            if (!PROVIDERS.contains(r.getProvider())) {
                throw new IllegalStateException("app.payment.payout-recipients : opérateur inconnu " + r.getProvider()
                    + " (attendu : " + PROVIDERS + ")");
            }
            if (r.getLabel() == null || r.getLabel().isBlank()) {
                throw new IllegalStateException("app.payment.payout-recipients : libellé manquant pour " + r.key());
            }
            if (!keys.add(r.key())) {
                throw new IllegalStateException("app.payment.payout-recipients : destinataire en double " + r.key());
            }
        }
    }

    public Optional<Recipient> find(String key) {
        return payoutRecipients.stream().filter(r -> r.key().equals(key)).findFirst();
    }

    @Getter
    @Setter
    public static class Recipient {
        private String label;
        /** 237XXXXXXXXX, sans + ni espaces. */
        private String msisdn;
        /** ORANGE_CMR ou MTN_MOMO_CMR. */
        private String provider;

        /**
         * Identifiant stable exposé au back-office (« ORANGE_CMR-2882 ») : il ne dépend pas de l'ordre
         * de la liste, et un numéro retiré de la configuration ne peut plus être choisi.
         */
        public String key() {
            return provider + "-" + (msisdn == null ? "" : msisdn.substring(Math.max(0, msisdn.length() - 4)));
        }
    }
}
