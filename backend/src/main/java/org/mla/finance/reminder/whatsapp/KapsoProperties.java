package org.mla.finance.reminder.whatsapp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WhatsApp via Kapso (proxy de l'API Cloud de Meta). Désactivé par défaut : sans KAPSO_ENABLED=true, les relances
 * partent par email seulement. Le numéro est partagé avec le suivi des tâches MLA (Apps Script « MLA - Planner App »),
 * qui lit ses réponses lui-même et ignore celles de MLA Finance (boutons « mlaf: »).
 */
@Component
@ConfigurationProperties(prefix = "app.kapso")
@Getter
@Setter
public class KapsoProperties {

    private boolean enabled;
    private String baseUrl = "https://api.kapso.ai";
    private String apiKey;
    private String phoneNumberId;
    /** Secret de signature du webhook Kapso (en-tête X-Webhook-Signature, HMAC-SHA256 hex du corps). */
    private String webhookSecret;
    private String language = "fr";
    private String templateMonthly = "mla_finance_relance_contribution";
    private String templateProject = "mla_finance_relance_collecte";
    private String templatePromise = "mla_finance_rappel_promesse";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    public boolean isReady() {
        return enabled && notBlank(apiKey) && notBlank(phoneNumberId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
