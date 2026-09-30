package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.service.WithdrawalFilter;
import org.mla.finance.service.WithdrawalService;
import org.mla.finance.web.dto.WithdrawalCreateRequest;
import org.mla.finance.web.dto.WithdrawalRejectRequest;
import org.mla.finance.web.dto.WithdrawalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @GetMapping
    public Page<WithdrawalResponse> list(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) WithdrawalStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return withdrawalService.search(new WithdrawalFilter(projectId, categoryId, status, dateFrom, dateTo), pageable);
    }

    @GetMapping("/{id}")
    public WithdrawalResponse get(@PathVariable Long id) {
        return withdrawalService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public ResponseEntity<WithdrawalResponse> create(@Valid @RequestBody WithdrawalCreateRequest request) {
        WithdrawalResponse created = withdrawalService.create(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('VALIDATE_WITHDRAWALS')")
    public WithdrawalResponse approve(@PathVariable Long id) {
        return withdrawalService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('VALIDATE_WITHDRAWALS')")
    public WithdrawalResponse reject(@PathVariable Long id, @Valid @RequestBody WithdrawalRejectRequest request) {
        return withdrawalService.reject(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse cancel(@PathVariable Long id) {
        return withdrawalService.cancel(id);
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse process(@PathVariable Long id) {
        return withdrawalService.process(id);
    }
}
