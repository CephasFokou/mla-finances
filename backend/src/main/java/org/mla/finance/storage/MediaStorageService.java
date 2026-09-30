package org.mla.finance.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Abstraction du stockage des fichiers médias, volontairement séparée de MediaAssetService : le
 * Cahier des Charges prévoit Cloudflare R2, mais aucune dépendance ni identifiant R2 n'existe
 * encore dans le projet (pom.xml ne référence aucun client S3/R2, application.yml n'a aucune
 * section de stockage objet). Plutôt que de poser une fausse fondation (même logique que pour la
 * génération de rapports asynchrone dans le module Rapports), c'est cette interface qui est
 * injectée dans MediaAssetService — la seule implémentation actuelle (LocalDiskMediaStorageService,
 * profil "dev") écrit sur le disque local du serveur. Le jour où R2 sera choisi et ses identifiants
 * disponibles, une nouvelle implémentation (ex. CloudflareR2MediaStorageService, @Profile("prod"))
 * suffira à brancher le vrai stockage, sans toucher à MediaAssetService ni à MediaController.
 */
public interface MediaStorageService {

    StoredMedia store(MultipartFile file) throws IOException;

    Resource load(String storageKey) throws IOException;
}
