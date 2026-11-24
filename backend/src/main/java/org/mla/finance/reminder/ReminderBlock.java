package org.mla.finance.reminder;

/**
 * Raison pour laquelle un membre ne peut pas être relancé maintenant (bouton grisé, motif au survol — RL-04).
 * {@code conflict} : blocage temporaire lié au délai d'attente (HTTP 409) ; sinon règle métier (HTTP 422).
 */
public enum ReminderBlock {
    DISABLED("RL-02", false, false),
    MONTH_NOT_CURRENT("RL-01", false, false),
    NOT_OPEN_YET("RL-06", false, true),
    PAID("RL-01", false, false),
    CLOSED("RL-05", false, false),
    PAYMENT_IN_PROGRESS("RL-09", true, false),
    QUEUED("RL-07", true, true),
    MAX_REACHED("RL-05", false, true),
    COOLDOWN("RL-04", true, true),
    NO_CONTACT("RL-03", false, false);

    private final String rule;
    private final boolean conflict;
    private final boolean soft;

    ReminderBlock(String rule, boolean conflict, boolean soft) {
        this.rule = rule;
        this.conflict = conflict;
        this.soft = soft;
    }

    /**
     * Règle par défaut que l'admin peut passer outre (« Relancer quand même », tracé dans l'historique) : délai
     * d'attente, maximum de relances, jour d'ouverture, rappel déjà programmé. Les autres blocages sont stricts.
     */
    public boolean soft() {
        return soft;
    }

    public String rule() {
        return rule;
    }

    public boolean conflict() {
        return conflict;
    }
}
