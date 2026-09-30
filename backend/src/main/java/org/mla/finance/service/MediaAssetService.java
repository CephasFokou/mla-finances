package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MediaAsset;
import org.mla.finance.domain.MediaCategory;
import org.mla.finance.domain.Project;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.repository.MediaAssetRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.storage.MediaStorageService;
import org.mla.finance.storage.StoredMedia;
import org.mla.finance.web.dto.MediaAssetResponse;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestion des médias (maquette "Médias & bannières") : bannières du back-office (avec ordre
 * d'affichage), visuels rattachés à un projet, et bibliothèque générale (uploads sans
 * rattachement, pour réutilisation manuelle ailleurs — aucune autre fonctionnalité du projet ne
 * consomme encore la bibliothèque automatiquement). Le contenu binaire est délégué à
 * MediaStorageService (voir sa javadoc pour le choix de stockage local en attendant R2) ; cette
 * classe ne connaît que les métadonnées.
 *
 * Comme les autres modules, jamais de suppression : desactivate() retire un média des listes
 * actives / de l'affichage public sans effacer le fichier ni la ligne, à la manière des catégories
 * de don et des utilisateurs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MediaAssetService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final MediaAssetRepository mediaAssetRepository;
    private final ProjectRepository projectRepository;
    private final MediaStorageService mediaStorageService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> list(MediaCategory category, boolean includeInactive) {
        List<MediaAsset> assets = category == MediaCategory.BANNER
            ? (includeInactive
                ? mediaAssetRepository.findByCategoryOrderByDisplayOrderAscUploadedAtDesc(category)
                : mediaAssetRepository.findByCategoryAndActiveTrueOrderByDisplayOrderAscUploadedAtDesc(category))
            : (includeInactive
                ? mediaAssetRepository.findByCategoryOrderByUploadedAtDesc(category)
                : mediaAssetRepository.findByCategoryAndActiveTrueOrderByUploadedAtDesc(category));
        return assets.stream().map(MediaAssetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> listByProject(Long projectId, boolean includeInactive) {
        List<MediaAsset> assets = includeInactive
            ? mediaAssetRepository.findByCategoryAndProjectIdOrderByUploadedAtDesc(MediaCategory.PROJECT_VISUAL, projectId)
            : mediaAssetRepository.findByCategoryAndProjectIdAndActiveTrueOrderByUploadedAtDesc(MediaCategory.PROJECT_VISUAL, projectId);
        return assets.stream().map(MediaAssetResponse::from).toList();
    }

    public MediaAssetResponse upload(MediaCategory category, MultipartFile file, String title, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("MEDIA_FILE_REQUIRED", "Un fichier est requis.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleViolationException("MEDIA_UNSUPPORTED_TYPE",
                "Type de fichier non supporté (jpeg, png, webp ou gif attendu) : " + contentType);
        }

        Project project = null;
        if (category == MediaCategory.PROJECT_VISUAL) {
            if (projectId == null) {
                throw new BusinessRuleViolationException("MEDIA_PROJECT_REQUIRED",
                    "Un projet est requis pour un visuel de projet.");
            }
            project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + projectId));
        } else if (projectId != null) {
            throw new BusinessRuleViolationException("MEDIA_PROJECT_NOT_ALLOWED",
                "Le rattachement à un projet ne s'applique qu'aux visuels de projet.");
        }

        Integer displayOrder = null;
        if (category == MediaCategory.BANNER) {
            displayOrder = mediaAssetRepository.findFirstByCategoryOrderByDisplayOrderDesc(MediaCategory.BANNER)
                .map(a -> a.getDisplayOrder() == null ? 0 : a.getDisplayOrder() + 1)
                .orElse(0);
        }

        StoredMedia stored;
        try {
            stored = mediaStorageService.store(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de l'enregistrement du fichier média", e);
        }

        MediaAsset asset = MediaAsset.builder()
            .category(category)
            .title(title)
            .fileName(file.getOriginalFilename())
            .storageKey(stored.storageKey())
            .contentType(contentType)
            .sizeBytes(stored.sizeBytes())
            .project(project)
            .displayOrder(displayOrder)
            .active(true)
            .build();
        asset = mediaAssetRepository.save(asset);

        auditLogService.record("MEDIA", asset.getId(), "UPLOAD",
            "Média '" + asset.getFileName() + "' ajouté (" + category.name() + ")");
        return MediaAssetResponse.from(asset);
    }

    public MediaAssetResponse activate(Long id) {
        MediaAsset asset = find(id);
        asset.setActive(true);
        auditLogService.record("MEDIA", id, "ACTIVATE", "Média '" + asset.getFileName() + "' réactivé");
        return MediaAssetResponse.from(asset);
    }

    public MediaAssetResponse deactivate(Long id) {
        MediaAsset asset = find(id);
        asset.setActive(false);
        auditLogService.record("MEDIA", id, "DEACTIVATE", "Média '" + asset.getFileName() + "' désactivé");
        return MediaAssetResponse.from(asset);
    }

    public void reorderBanners(List<Long> orderedIds) {
        List<MediaAsset> banners = mediaAssetRepository.findByCategoryOrderByDisplayOrderAscUploadedAtDesc(MediaCategory.BANNER);
        Set<Long> existingIds = banners.stream().map(MediaAsset::getId).collect(Collectors.toSet());
        if (banners.size() != orderedIds.size() || !existingIds.containsAll(orderedIds)) {
            throw new BusinessRuleViolationException("MEDIA_REORDER_MISMATCH",
                "La liste fournie doit contenir exactement l'ensemble des bannières existantes.");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            MediaAsset asset = banners.stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow();
            asset.setDisplayOrder(i);
        }
        // audit_logs.entity_id est non-nul et rattaché à une seule entité ; pour cette action
        // groupée, la première bannière de la nouvelle liste sert d'ancre nominale, le résumé
        // décrit l'action complète en texte.
        auditLogService.record("MEDIA", orderedIds.get(0), "REORDER_BANNERS", "Ordre d'affichage des bannières mis à jour");
    }

    @Transactional(readOnly = true)
    public MediaFile loadFile(Long id) {
        MediaAsset asset = find(id);
        try {
            Resource resource = mediaStorageService.load(asset.getStorageKey());
            return new MediaFile(resource, asset.getContentType(), asset.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException("Fichier média introuvable sur le stockage : " + asset.getStorageKey(), e);
        }
    }

    private MediaAsset find(Long id) {
        return mediaAssetRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Média introuvable : " + id));
    }

    public record MediaFile(Resource resource, String contentType, String fileName) {
    }
}
