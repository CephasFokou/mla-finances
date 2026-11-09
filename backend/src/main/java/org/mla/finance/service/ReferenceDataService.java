package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.DonorRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.web.dto.CategoryResponse;
import org.mla.finance.web.dto.DonorRef;
import org.mla.finance.web.dto.PaymentChannelRef;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Listes en lecture seule qui alimentent les listes déroulantes des formulaires. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final DonationCategoryRepository categoryRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final DonorRepository donorRepository;

    public List<CategoryResponse> categories(boolean includeInactive) {
        var categories = includeInactive
            ? categoryRepository.findAllByOrderByNameAsc()
            : categoryRepository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    public List<PaymentChannelRef> paymentChannels() {
        return paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().stream().map(PaymentChannelRef::from).toList();
    }

    public List<DonorRef> donors(String query) {
        String q = query == null ? "" : query.trim();
        return donorRepository.search(q, PageRequest.of(0, 30)).stream().map(DonorRef::from).toList();
    }
}
