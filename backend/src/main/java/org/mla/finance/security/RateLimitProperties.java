package org.mla.finance.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Section app.rate-limit d'application.yml (voir RateLimitFilter). */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean trustProxy = false;
    private int publicRequestsPerMinute = 60;
    private int authRequestsPerMinute = 20;
    private int webhookRequestsPerMinute = 120;
}
