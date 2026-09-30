package org.mla.finance.storage;

/**
 * Résultat d'un stockage réussi : la clé à conserver en base (MediaAsset.storageKey) et la taille
 * réelle écrite sur le support de stockage, pour ne pas faire confiance à la taille annoncée par
 * le client HTTP.
 */
public record StoredMedia(String storageKey, long sizeBytes) {
}
