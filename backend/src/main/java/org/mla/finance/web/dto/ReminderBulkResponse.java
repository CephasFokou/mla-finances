package org.mla.finance.web.dto;

import java.util.List;

/** Compte rendu d'une relance groupée (RL-11) : relancés, et ignorés avec leur motif. */
public record ReminderBulkResponse(List<Sent> sent, List<Skipped> skipped) {

    public record Sent(Long donorId, String label, boolean delivered, boolean queued) {
    }

    public record Skipped(Long donorId, String label, String reason) {
    }
}
