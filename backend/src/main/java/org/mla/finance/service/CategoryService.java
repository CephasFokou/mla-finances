package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.web.dto.CategoryRequest;
import org.mla.finance.web.dto.CategoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des catégories de don (l'alternative au projet dans le rattachement XOR, RG-02).
 * Aucune suppression : une catégorie déjà utilisée par des contributions ne peut pas disparaître
 * de l'historique — on la désactive (active=false), ce qui la retire des listes de saisie sans
 * toucher aux contributions existantes qui la référencent encore.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final DonationCategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        List<DonationCategory> categories = includeInactive
            ? categoryRepository.findAllByOrderByNameAsc()
            : categoryRepository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleViolationException("CATEGORY_UNIQUE",
                "Une catégorie de don nommée '" + request.name() + "' existe déjà.");
        }
        DonationCategory category = DonationCategory.builder()
            .name(request.name())
            .description(request.description())
            .active(true)
            .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        DonationCategory category = find(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessRuleViolationException("CATEGORY_UNIQUE",
                "Une catégorie de don nommée '" + request.name() + "' existe déjà.");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    public CategoryResponse activate(Long id) {
        DonationCategory category = find(id);
        category.setActive(true);
        auditLogService.record("CATEGORY", id, "ACTIVATE", "Catégorie '" + category.getName() + "' réactivée");
        return CategoryResponse.from(category);
    }

    public CategoryResponse deactivate(Long id) {
        DonationCategory category = find(id);
        category.setActive(false);
        auditLogService.record("CATEGORY", id, "DEACTIVATE", "Catégorie '" + category.getName() + "' désactivée");
        return CategoryResponse.from(category);
    }

    private DonationCategory find(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Catégorie introuvable : " + id));
    }
}
