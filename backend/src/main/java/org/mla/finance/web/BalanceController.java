package org.mla.finance.web;

import lombok.RequiredArgsConstructor;
import org.mla.finance.service.BalanceService;
import org.mla.finance.web.dto.CategoryBalanceResponse;
import org.mla.finance.web.dto.ProjectBalanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/api/v1/projects/{id}/balance")
    public ProjectBalanceResponse projectBalance(@PathVariable Long id) {
        return balanceService.projectBalance(id);
    }

    @GetMapping("/api/v1/categories/{id}/balance")
    public CategoryBalanceResponse categoryBalance(@PathVariable Long id) {
        return balanceService.categoryBalance(id);
    }
}
