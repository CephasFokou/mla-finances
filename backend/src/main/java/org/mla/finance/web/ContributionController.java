package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.MovementType;
import org.mla.finance.service.ContributionCsvExporter;
import org.mla.finance.service.ContributionFilter;
import org.mla.finance.service.ContributionService;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.ContributionRequest;
import org.mla.finance.web.dto.ContributionResponse;
import org.mla.finance.web.dto.ContributionUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ContributionService contributionService;
    private final ContributionCsvExporter csvExporter;

    @GetMapping
    public Page<ContributionResponse> list(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long donorId,
        @RequestParam(required = false) Long paymentChannelId,
        @RequestParam(required = false) MovementType movementType,
        @RequestParam(required = false) Boolean anonymous,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) MovementNature nature,
        @RequestParam(required = false) Long activityId,
        @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return contributionService.search(
            new ContributionFilter(projectId, categoryId, donorId, paymentChannelId, movementType,
                anonymous, periodMonth, dateFrom, dateTo, nature, activityId),
            pageable);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long donorId,
        @RequestParam(required = false) Long paymentChannelId,
        @RequestParam(required = false) MovementType movementType,
        @RequestParam(required = false) Boolean anonymous,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) MovementNature nature,
        @RequestParam(required = false) Long activityId
    ) {
        String csv = csvExporter.export(
            new ContributionFilter(projectId, categoryId, donorId, paymentChannelId, movementType,
                anonymous, periodMonth, dateFrom, dateTo, nature, activityId));
        return ResponseEntity.ok()
            .contentType(TEXT_CSV_UTF8)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("contributions.csv").build().toString())
            .body(csv);
    }

    @GetMapping("/{id}")
    public ContributionResponse get(@PathVariable Long id) {
        return contributionService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public ResponseEntity<ContributionResponse> create(@Valid @RequestBody ContributionRequest request) {
        ContributionResponse created = contributionService.create(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public ContributionResponse update(@PathVariable Long id, @Valid @RequestBody ContributionUpdateRequest request) {
        return contributionService.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public ResponseEntity<ContributionResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        ContributionResponse adjustment = contributionService.cancel(id, request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/contributions/{id}").buildAndExpand(adjustment.id()).toUri())
            .body(adjustment);
    }
}
