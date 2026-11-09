package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.service.TransferService;
import org.mla.finance.web.dto.TransferRequest;
import org.mla.finance.web.dto.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RG-10. Un transfert déplace de l'argent déjà détenu : il demande à la fois de pouvoir gérer et
 * valider les retraits. Les écritures créées se retrouvent ensuite dans les listes de contributions
 * et de retraits (filtre nature=TRANSFER).
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS') and hasAuthority('VALIDATE_WITHDRAWALS')")
    public TransferResponse create(@Valid @RequestBody TransferRequest request) {
        return transferService.create(request);
    }
}
