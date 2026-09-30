package org.mla.finance.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Implémentation de développement de MediaStorageService : écrit les fichiers sur le disque local
 * (répertoire configurable via app.media.storage-dir, par défaut ./data/media-storage), en dehors
 * du classpath pour survivre aux redémarrages de l'application — contrairement à la base H2 en
 * mémoire du profil dev, qui repart à vide à chaque redémarrage (voir l'échange avec Cephas sur le
 * module Contributions). Ne convient ni à un déploiement multi-instance ni à la production : voir
 * la javadoc de MediaStorageService pour la marche à suivre le moment venu.
 */
@Service
@Profile("dev")
public class LocalDiskMediaStorageService implements MediaStorageService {

    private final Path root;

    public LocalDiskMediaStorageService(@Value("${app.media.storage-dir:./data/media-storage}") String storageDir) throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredMedia store(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        Path target = root.resolve(storageKey);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredMedia(storageKey, Files.size(target));
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new IOException("Fichier introuvable : " + storageKey);
        }
        return new FileSystemResource(target);
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
