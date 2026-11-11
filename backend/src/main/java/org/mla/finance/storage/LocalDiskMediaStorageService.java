package org.mla.finance.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Implémentation « disque local » de MediaStorageService : écrit les fichiers dans le répertoire
 * app.media.storage-dir (par défaut ./data/media-storage), en dehors du classpath pour survivre
 * aux redémarrages de l'application.
 * <p>
 * Active dans tous les profils tant que app.media.storage=local (valeur par défaut). En
 * déploiement Railway, le répertoire doit être porté par un volume persistant (monté sur
 * /app/data/media-storage), sinon les fichiers disparaissent à chaque redéploiement. Ne convient
 * pas à un déploiement multi-instance : une implémentation objet (Cloudflare R2 / S3) pourra être
 * ajoutée avec app.media.storage=s3 sans toucher à MediaAssetService (voir
 * claude/plan-de-deploiement.md).
 */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
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
