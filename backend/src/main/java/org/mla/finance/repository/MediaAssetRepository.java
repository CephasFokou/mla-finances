package org.mla.finance.repository;

import org.mla.finance.domain.MediaAsset;
import org.mla.finance.domain.MediaCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByCategoryOrderByDisplayOrderAscUploadedAtDesc(MediaCategory category);

    List<MediaAsset> findByCategoryAndActiveTrueOrderByDisplayOrderAscUploadedAtDesc(MediaCategory category);

    List<MediaAsset> findByCategoryOrderByUploadedAtDesc(MediaCategory category);

    List<MediaAsset> findByCategoryAndActiveTrueOrderByUploadedAtDesc(MediaCategory category);

    List<MediaAsset> findByCategoryAndProjectIdOrderByUploadedAtDesc(MediaCategory category, Long projectId);

    List<MediaAsset> findByCategoryAndProjectIdAndActiveTrueOrderByUploadedAtDesc(MediaCategory category, Long projectId);

    Optional<MediaAsset> findFirstByCategoryOrderByDisplayOrderDesc(MediaCategory category);
}
