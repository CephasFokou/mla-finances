package org.mla.finance.web;

import lombok.RequiredArgsConstructor;
import org.mla.finance.reminder.ReminderPublicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Liens des relances, côté site de don (public, limité par IP comme tout /api/v1/public/**). */
@RestController
@RequiredArgsConstructor
public class ReminderPublicController {

    private final ReminderPublicService service;

    public record ActionRequest(String action, LocalDate date) {
    }

    @GetMapping("/api/v1/public/reminder-actions/{token}")
    public ReminderPublicService.ActionView view(@PathVariable String token) {
        return service.view(token);
    }

    @PostMapping("/api/v1/public/reminder-actions/{token}")
    public ReminderPublicService.ActionResult act(@PathVariable String token, @RequestBody ActionRequest request) {
        return service.act(token, request.action(), request.date());
    }

    @GetMapping("/api/v1/public/contribution-link/{token}")
    public ReminderPublicService.ContributionLink contributionLink(@PathVariable String token) {
        return service.contributionLink(token);
    }
}
