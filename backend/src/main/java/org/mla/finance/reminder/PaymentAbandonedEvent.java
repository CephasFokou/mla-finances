package org.mla.finance.reminder;

import java.time.LocalDate;
import java.util.List;

/**
 * Paiement échoué (PawaPay) ou abandonné (back-office) d'un membre : les mois qu'il couvrait (contribution mensuelle)
 * ou la collecte visée (don rattaché au membre).
 */
public record PaymentAbandonedEvent(Long donorId, List<LocalDate> months, Long projectId) {
}
