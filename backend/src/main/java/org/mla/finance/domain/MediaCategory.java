package org.mla.finance.domain;

/**
 * Catégorie d'un média (correspond aux écrans de la maquette "Médias & bannières") : bannière du
 * back-office (avec ordre d'affichage), visuel rattaché à un projet, ou élément de la bibliothèque
 * générale. Détermine quels champs de MediaAsset s'appliquent (displayOrder pour BANNER, project
 * pour PROJECT_VISUAL).
 */
public enum MediaCategory {
    BANNER,
    PROJECT_VISUAL,
    LIBRARY
}
