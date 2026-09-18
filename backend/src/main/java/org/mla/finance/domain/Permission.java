package org.mla.finance.domain;

/**
 * Permissions clés du back-office (cf. Document Technique, matrice rôles × permissions
 * maquettée dans l'écran Administration → Rôles & permissions).
 */
public enum Permission {
    VIEW_DASHBOARD,
    MANAGE_CONTRIBUTIONS,
    MANAGE_PROJECTS,
    MANAGE_WITHDRAWALS,
    VALIDATE_WITHDRAWALS,
    MANAGE_USERS,
    MANAGE_MEDIA,
    VIEW_AUDIT_LOG
}
