package org.mla.finance.domain;

/** Membres concernés par les relances d'une collecte (cadrage Relances §6 bis). */
public enum ProjectReminderAudience {
    /** Tous les membres actifs. */
    ALL,
    /** Responsables seulement. */
    RESPONSABLES,
    /** Membres (hors responsables) seulement. */
    MEMBRES,
    /** Sélection manuelle (table project_reminder_members). */
    SELECTION;

    public boolean includes(Donor donor, java.util.Set<Long> selection) {
        return switch (this) {
            case ALL -> true;
            case RESPONSABLES -> donor.getMemberRole() == MemberRole.RESPONSABLE;
            case MEMBRES -> donor.getMemberRole() == MemberRole.MEMBRE;
            case SELECTION -> selection.contains(donor.getId());
        };
    }
}
