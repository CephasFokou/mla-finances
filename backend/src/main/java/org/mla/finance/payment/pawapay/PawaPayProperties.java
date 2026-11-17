package org.mla.finance.payment.pawapay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Section app.payment.pawapay d'application.yml (valeurs par défaut et commentaires là-bas). */
@Component
@ConfigurationProperties(prefix = "app.payment.pawapay")
@Getter
@Setter
public class PawaPayProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.sandbox.pawapay.io";
    private String apiToken = "";
    private String callbackSecret = "";
    private String currency = "XAF";
    private String country = "CMR";
    /** Code canal MLA (payment_channels.code) -> code opérateur PawaPay (ex. MTN_MOMO -> MTN_MOMO_CMR). */
    private Map<String, String> providers = new LinkedHashMap<>();
    private BigDecimal minAmount = new BigDecimal("100");
    private BigDecimal maxAmount = new BigDecimal("500000");
    private int maxInitiationsPerPhone = 3;
    private long pollIntervalMs = 15_000;
    private int pendingTimeoutMinutes = 30;
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 15_000;

    public boolean hasApiToken() {
        return apiToken != null && !apiToken.isBlank();
    }

    public boolean hasCallbackSecret() {
        return callbackSecret != null && !callbackSecret.isBlank();
    }
}
