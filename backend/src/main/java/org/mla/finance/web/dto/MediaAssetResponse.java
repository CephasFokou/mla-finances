package org.mla.finance.web.dto;

import org.mla.finance.domain.MediaAsset;

import java.time.Instant;

public record MediaAssetResponse(
    Long id,
    String category,
    String title,
    String fileName,
    String contentType,
    long sizeBytes,
    ProjectRef project,
    Integer displayOrder,
    boolean active,
    Instant uploadedAt
) {
    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(
            asset.getId(),
            asset.getCategory().name(),
            asset.getTitle(),
            asset.getFileName(),
            asset.getContentType(),
            asset.getSizeBytes(),
            ProjectRef.from(asset.getProject()),
            asset.getDisplayOrder(),
            asset.isActive(),
            asset.getUploadedAt()
        );
    }
}
