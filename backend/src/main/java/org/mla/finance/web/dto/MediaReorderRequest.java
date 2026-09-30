package org.mla.finance.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Nouvel ordre d'affichage des bannières : la liste complète des ids, dans l'ordre voulu (le
 * premier élément recevra displayOrder=0, etc.) — pas de mise à jour incrémentale, sur le même
 * principe que UserRolesUpdateRequest (le frontend renvoie l'ensemble complet).
 */
public record MediaReorderRequest(@NotEmpty List<Long> orderedIds) {
}
