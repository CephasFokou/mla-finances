package org.mla.finance.reminder;

/**
 * Raison pour laquelle un membre ne peut pas être relancé maintenant (bouton grisé, motif au survol — RL-04).
 * {@code conflict} : blocage temporaire lié au délai d'attente (HTTP 409) ; sinon règle métier (HTTP 422).
 */
public enum ReminderBlock {
    DISABLED("RL-02", false),
    MONTH_NOT_CURRENT("RL-01", false),
    NOT_OPEN_YET("RL-06", false),
    PAID("RL-01", false),
    CLOSED("RL-05", false),
    PAYMENT_IN_PROGRESS("RL-09", true),
    QUEUED("RL-07", true),
    MAX_REACHED("RL-05", false),
    COOLDOWN("RL-04", true),
    NO_CONTACT("RL-03", false);

    private final String rule;
    private final boolean conflict;

    ReminderBlock(String rule, boolean conflict) {
        this.rule = rule;
        this.conflict = conflict;
    }

    public String rule() {
        return rule;
    }

    public boolean conflict() {
        return conflict;
    }
}
