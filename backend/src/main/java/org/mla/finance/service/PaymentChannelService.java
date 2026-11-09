package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.web.dto.PaymentChannelRequest;
import org.mla.finance.web.dto.PaymentChannelResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RG-15 : canaux de paiement dynamiques. Un canal n'est jamais supprimé (les mouvements passés le
 * référencent) : on le désactive, il disparaît alors des formulaires de saisie.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentChannelService {

    private final PaymentChannelRepository paymentChannelRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<PaymentChannelResponse> listAll() {
        return paymentChannelRepository.findAllByOrderBySortOrderAsc().stream().map(PaymentChannelResponse::from).toList();
    }

    public PaymentChannelResponse create(PaymentChannelRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new BusinessRuleViolationException("RG-15", "Le code du canal est obligatoire.");
        }
        if (paymentChannelRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Un canal avec le code " + request.code() + " existe déjà.");
        }
        PaymentChannel saved = paymentChannelRepository.save(PaymentChannel.builder()
            .code(request.code())
            .label(request.label().trim())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : paymentChannelRepository.maxSortOrder() + 1)
            .active(true)
            .build());
        auditLogService.record("PAYMENT_CHANNEL", saved.getId(), "CREATE", "Canal « " + saved.getLabel() + " » ajouté");
        return PaymentChannelResponse.from(saved);
    }

    public PaymentChannelResponse update(Long id, PaymentChannelRequest request) {
        PaymentChannel channel = find(id);
        channel.setLabel(request.label().trim());
        if (request.sortOrder() != null) {
            channel.setSortOrder(request.sortOrder());
        }
        auditLogService.record("PAYMENT_CHANNEL", id, "UPDATE", "Canal « " + channel.getLabel() + " » modifié");
        return PaymentChannelResponse.from(channel);
    }

    public PaymentChannelResponse setActive(Long id, boolean active) {
        PaymentChannel channel = find(id);
        if (!active && LedgerLookup.OTHER_CHANNEL_CODE.equals(channel.getCode())) {
            throw new BusinessRuleViolationException("RG-15", "Le canal « Autre » sert aux transferts internes et reste actif.");
        }
        channel.setActive(active);
        auditLogService.record("PAYMENT_CHANNEL", id, active ? "ACTIVATE" : "DEACTIVATE",
            "Canal « " + channel.getLabel() + " » " + (active ? "réactivé" : "désactivé"));
        return PaymentChannelResponse.from(channel);
    }

    private PaymentChannel find(Long id) {
        return paymentChannelRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Canal de paiement introuvable : " + id));
    }
}
