package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.payment.pawapay.PawaPayReconciliationService;
import org.mla.finance.service.PaymentTransactionService;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.PaymentTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Traitement back-office des dons reçus via le front-office public (voir PublicController côté
 * lecture/soumission, sans authentification). Lecture ouverte à tout utilisateur connecté, comme
 * les autres modules ; confirmation/annulation réservées à MANAGE_CONTRIBUTIONS (aucune permission
 * dédiée n'existe encore pour ce module).
 */
@RestController
@RequestMapping("/api/v1/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;
    private final PawaPayReconciliationService reconciliationService;

    @GetMapping
    public Page<PaymentTransactionResponse> list(
        @RequestParam(required = false) PaymentTransactionStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return paymentTransactionService.search(status, pageable);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public PaymentTransactionResponse confirm(@PathVariable Long id) {
        return paymentTransactionService.confirm(id);
    }

    /**
     * Relecture immédiate du statut chez PawaPay pour un don en ligne (callback perdu, don « à
     * vérifier »...). Idempotent : ne fait que ce que le polling aurait fait.
     */
    @PostMapping("/{id}/recheck")
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public PaymentTransactionResponse recheck(@PathVariable Long id) {
        PaymentTransactionResponse current = paymentTransactionService.get(id);
        if (!current.online()) {
            throw new BusinessRuleViolationException("DON-07", "Ce don n'est pas payé en ligne : confirmez-le ou annulez-le manuellement.");
        }
        reconciliationService.reconcile(id);
        // Relecture dans une transaction dédiée : l'entité renvoyée par la réconciliation est détachée
        // (associations paresseuses non chargeables ici, open-in-view désactivé).
        return paymentTransactionService.get(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_CONTRIBUTIONS')")
    public PaymentTransactionResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return paymentTransactionService.cancel(id, request);
    }
}
