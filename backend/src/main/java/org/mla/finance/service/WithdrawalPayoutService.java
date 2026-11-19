package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.payment.pawapay.MobileMoneyBalance;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.payment.pawapay.PayoutOutcome;
import org.mla.finance.payment.payout.MsisdnMask;
import org.mla.finance.payment.payout.PayoutRecipientsProperties;
import org.mla.finance.payment.payout.PayoutRecipientsProperties.Recipient;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.MobileMoneyBalanceResponse;
import org.mla.finance.web.dto.PayoutOptionsResponse;
import org.mla.finance.web.dto.WithdrawalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;

/**
 * Décaissement d'un retrait APPROVED par Mobile Money via wallet-ledger (POST /v2/payouts). Point
 * d'entrée unique pour l'envoi et pour les trois déclencheurs de relecture — webhook wallet-ledger,
 * job de polling, bouton « Vérifier le statut » — qui ne font jamais confiance qu'à une lecture
 * directe de GET /v2/payouts/{payoutId} avec la clé du projet MLA.
 * <p>
 * Pas de transaction ici : l'appel réseau (jusqu'au read-timeout) ne se fait jamais sous verrou.
 * Séquence de l'envoi : (1) WithdrawalPayoutStateService.prepare enregistre le payoutId et commit ;
 * (2) appel à wallet-ledger ; (3) application de la réponse sous verrou. Si l'API tombe entre (1) et
 * (3), le payout reste PROCESSING et le polling tranche (NOT_FOUND => FAILED).
 */
@Service
@RequiredArgsConstructor
public class WithdrawalPayoutService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPayoutService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalPayoutStateService stateService;
    private final WithdrawalService withdrawalService;
    private final PawaPayClient client;
    private final PawaPayProperties pawaPayProperties;
    private final PayoutRecipientsProperties recipients;

    public PayoutOptionsResponse options() {
        if (!pawaPayProperties.isEnabled()) {
            return new PayoutOptionsResponse(false, java.util.List.of());
        }
        return new PayoutOptionsResponse(true, recipients.getPayoutRecipients().stream()
            .map(r -> new PayoutOptionsResponse.Recipient(r.key(), r.getLabel(), r.getProvider(), MsisdnMask.mask(r.getMsisdn())))
            .toList());
    }

    public MobileMoneyBalanceResponse balance() {
        if (!pawaPayProperties.isEnabled()) {
            return MobileMoneyBalanceResponse.disabled();
        }
        return client.balance().map(MobileMoneyBalanceResponse::from).orElseGet(MobileMoneyBalanceResponse::unreachable);
    }

    public WithdrawalResponse initiate(Long withdrawalId, String recipientKey) {
        requireEnabled();
        Recipient recipient = recipients.find(recipientKey).orElseThrow(() -> new BusinessRuleViolationException("PAYOUT_RECIPIENT",
            "Destinataire non approuvé : choisissez un des numéros Mobile Money du MLA proposés."));
        Withdrawal prepared = stateService.prepare(withdrawalId, recipient);
        String payoutId = prepared.getPayoutId();
        String amount = prepared.getAmount().stripTrailingZeros().toPlainString();
        log.info("Payout {} retrait #{} montant={} XAF destinataire={} opérateur={}", payoutId, withdrawalId, amount,
            PawaPayClient.maskPhone(recipient.getMsisdn()), recipient.getProvider());
        PayoutOutcome outcome = client.initiatePayout(payoutId, amount, recipient.getMsisdn(), recipient.getProvider(),
            clientReferenceId(prepared), customerMessage(withdrawalId));
        stateService.apply(payoutId, outcome);
        return withdrawalService.get(withdrawalId);
    }

    /** Bouton « Vérifier le statut ». */
    public WithdrawalResponse recheck(Long withdrawalId) {
        requireEnabled();
        Withdrawal w = withdrawalRepository.findById(withdrawalId)
            .orElseThrow(() -> new EntityNotFoundException("Retrait introuvable : " + withdrawalId));
        if (w.getPayoutId() == null) {
            throw new BusinessRuleViolationException("PAYOUT_NONE", "Ce retrait n'a pas de décaissement Mobile Money.");
        }
        reconcile(w);
        return withdrawalService.get(withdrawalId);
    }

    /** Webhook wallet-ledger : ne lit que le payoutId, puis relit le statut. Vide si payoutId inconnu. */
    public Optional<Withdrawal> reconcileByPayoutId(String payoutId) {
        return withdrawalRepository.findByPayoutId(payoutId).flatMap(this::reconcile);
    }

    /** Job de polling. */
    public Optional<Withdrawal> reconcile(Long withdrawalId) {
        return withdrawalRepository.findById(withdrawalId).flatMap(this::reconcile);
    }

    private Optional<Withdrawal> reconcile(Withdrawal w) {
        if (!w.hasActivePayout()) {
            return Optional.of(w);
        }
        PayoutOutcome outcome = client.checkPayout(w.getPayoutId());
        log.debug("Relecture payout {} (retrait #{}) : {} ({})", w.getPayoutId(), w.getId(), outcome.kind(), outcome.providerStatus());
        return stateService.apply(w.getPayoutId(), outcome);
    }

    private void requireEnabled() {
        if (!pawaPayProperties.isEnabled()) {
            throw new BusinessRuleViolationException("PAYOUT_DISABLED",
                "Le décaissement Mobile Money n'est pas activé (mode manuel) : utilisez « Marquer comme décaissé ».");
        }
    }

    /** MLA-WD-<référence du retrait>, ou MLA-WD-<id> pour un retrait sans référence. */
    static String clientReferenceId(Withdrawal w) {
        String ref = w.getReference() != null && !w.getReference().isBlank() ? w.getReference().trim() : String.valueOf(w.getId());
        return "MLA-WD-" + ref;
    }

    /** « MLA RETRAIT 123 » : alphanumérique + espaces, 4 à 22 caractères (contrainte PawaPay). */
    static String customerMessage(Long withdrawalId) {
        String message = "MLA RETRAIT " + withdrawalId;
        return message.length() > 22 ? message.substring(0, 22).trim() : message;
    }
}
