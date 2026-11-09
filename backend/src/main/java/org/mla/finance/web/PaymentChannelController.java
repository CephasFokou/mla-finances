package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.service.PaymentChannelService;
import org.mla.finance.web.dto.PaymentChannelRequest;
import org.mla.finance.web.dto.PaymentChannelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RG-15 : administration des canaux de paiement. La liste des canaux actifs pour les formulaires
 * reste GET /api/v1/payment-channels (ReferenceDataController).
 */
@RestController
@RequestMapping("/api/v1/payment-channels")
@RequiredArgsConstructor
public class PaymentChannelController {

    private final PaymentChannelService paymentChannelService;

    @GetMapping("/all")
    public List<PaymentChannelResponse> listAll() {
        return paymentChannelService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public PaymentChannelResponse create(@Valid @RequestBody PaymentChannelRequest request) {
        return paymentChannelService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public PaymentChannelResponse update(@PathVariable Long id, @Valid @RequestBody PaymentChannelRequest request) {
        return paymentChannelService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public PaymentChannelResponse activate(@PathVariable Long id) {
        return paymentChannelService.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public PaymentChannelResponse deactivate(@PathVariable Long id) {
        return paymentChannelService.setActive(id, false);
    }
}
