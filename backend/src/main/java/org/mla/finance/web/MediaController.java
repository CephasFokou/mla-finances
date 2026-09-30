package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MediaCategory;
import org.mla.finance.service.MediaAssetService;
import org.mla.finance.web.dto.MediaAssetResponse;
import org.mla.finance.web.dto.MediaReorderRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Médias (bannières, visuels de projet, bibliothèque). Voir MediaAssetService et
 * MediaStorageService pour les décisions de conception (pas de R2 encore, stockage local en dev).
 * GET /{id}/file n'a volontairement pas de @PreAuthorize : les images doivent être chargeables
 * directement par une balise &lt;img&gt; sans en-tête d'autorisation — voir le point à confirmer
 * dans claude/module-medias-spec.md une fois l'authentification réelle branchée.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaAssetService mediaAssetService;

    @GetMapping
    public List<MediaAssetResponse> list(
        @RequestParam MediaCategory category,
        @RequestParam(required = false, defaultValue = "false") boolean includeInactive
    ) {
        return mediaAssetService.list(category, includeInactive);
    }

    @GetMapping("/project/{projectId}")
    public List<MediaAssetResponse> listByProject(
        @PathVariable Long projectId,
        @RequestParam(required = false, defaultValue = "false") boolean includeInactive
    ) {
        return mediaAssetService.listByProject(projectId, includeInactive);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MANAGE_MEDIA')")
    public MediaAssetResponse upload(
        @RequestParam MediaCategory category,
        @RequestParam MultipartFile file,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) Long projectId
    ) {
        return mediaAssetService.upload(category, file, title, projectId);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MANAGE_MEDIA')")
    public MediaAssetResponse activate(@PathVariable Long id) {
        return mediaAssetService.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MANAGE_MEDIA')")
    public MediaAssetResponse deactivate(@PathVariable Long id) {
        return mediaAssetService.deactivate(id);
    }

    @PutMapping("/banners/order")
    @PreAuthorize("hasAuthority('MANAGE_MEDIA')")
    public ResponseEntity<Void> reorderBanners(@Valid @RequestBody MediaReorderRequest request) {
        mediaAssetService.reorderBanners(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        MediaAssetService.MediaFile file = mediaAssetService.loadFile(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
            .body(file.resource());
    }
}
