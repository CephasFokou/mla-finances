package org.mla.finance.web;

import lombok.RequiredArgsConstructor;
import org.mla.finance.service.AuditLogFilter;
import org.mla.finance.service.ReportsService;
import org.mla.finance.web.dto.AuditLogResponse;
import org.mla.finance.web.dto.ProjectMonthlyReportResponse;
import org.mla.finance.web.dto.ReportsOverviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/overview")
    public ReportsOverviewResponse overview() {
        return reportsService.overview();
    }

    @GetMapping("/projects/{id}/monthly")
    public ProjectMonthlyReportResponse projectMonthly(
        @PathVariable Long id,
        @RequestParam(defaultValue = "6") int months
    ) {
        int clamped = Math.max(1, Math.min(months, 24));
        return reportsService.projectMonthly(id, clamped);
    }

    @GetMapping("/audit")
    public Page<AuditLogResponse> audit(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) Long entityId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return reportsService.auditLog(new AuditLogFilter(entityType, entityId, action, dateFrom, dateTo), pageable);
    }
}
