package org.mla.finance.reminder;

import java.time.LocalDate;
import java.util.List;

/** Paiement de contribution mensuelle échoué (PawaPay) ou abandonné (back-office) : les mois qu'il couvrait. */
public record PaymentAbandonedEvent(Long donorId, List<LocalDate> months) {
}
