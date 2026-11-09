package org.mla.finance.web.dto;

import org.mla.finance.domain.DonationCategory;

public record CategoryResponse(Long id, String name, String description, boolean active, boolean mainFund) {
    public static CategoryResponse from(DonationCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.isMainFund());
    }
}
