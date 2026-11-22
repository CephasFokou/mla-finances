package org.mla.finance.reminder;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/** Heure courante des relances, isolée pour les tests (plage horaire, ouverture le 10, fin de mois). */
@Component
public class ReminderClock {

    /** Fuseau de référence du MLA : échéances, plage horaire 8 h - 20 h, ouverture le 10 du mois. */
    public static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    public Instant now() {
        return Instant.now();
    }
}
