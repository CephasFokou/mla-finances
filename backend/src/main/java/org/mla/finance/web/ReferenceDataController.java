package org.mla.finance.web;

import lombok.RequiredArgsConstructor;
import org.mla.finance.service.ReferenceDataService;
import org.mla.finance.web.dto.CategoryResponse;
import org.mla.finance.web.dto.DonorRef;
import org.mla.finance.web.dto.PaymentChannelRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Listes en lecture qui alimentent les listes déroulantes des formulaires. La liste des projets
 * (GET /api/v1/projects) est passée à ProjectController, qui expose aussi la gestion complète du
 * module "Projets & catégories".
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return referenceDataService.categories(includeInactive);
    }

    @GetMapping("/payment-channels")
    public List<PaymentChannelRef> paymentChannels() {
        return referenceDataService.paymentChannels();
    }

    @GetMapping("/donors")
    public List<DonorRef> donors(@RequestParam(required = false) String q) {
        return referenceDataService.donors(q);
    }
}
