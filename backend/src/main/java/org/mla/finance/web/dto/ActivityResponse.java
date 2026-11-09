package org.mla.finance.web.dto;

import org.mla.finance.domain.Activity;
import org.mla.finance.domain.ActivityStatus;

import java.time.LocalDate;

public record ActivityResponse(Long id, String name, String periodLabel, LocalDate eventDate, ActivityStatus status, String comment) {
    public static ActivityResponse from(Activity a) {
        return new ActivityResponse(a.getId(), a.getName(), a.getPeriodLabel(), a.getEventDate(), a.getStatus(), a.getComment());
    }
}
