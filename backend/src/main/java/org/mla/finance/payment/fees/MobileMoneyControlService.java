package org.mla.finance.payment.fees;

import lombok.RequiredArgsConstructor;
import org.mla.finance.payment.pawapay.MobileMoneyBalance;
import org.mla.finance.payment.pawapay.PawaPayProvider;
import org.mla.finance.repository.MobileMoneyFeeRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrôle d'écart entre le solde Mobile Money du MLA chez wallet-ledger et ce que MLA Finance en
 * attend depuis la bascule (date d'ouverture wallet-ledger, sinon app.payment.fees.ledger-start) :
 * <pre>
 *   attendu = solde d'ouverture + dons Mobile Money confirmés − décaissements Mobile Money reçus − frais comptés
 *   écart   = (disponible + réservé chez wallet-ledger) − attendu
 * </pre>
 * Le réservé est rajouté parce que MLA Finance ne compte un décaissement qu'une fois reçu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileMoneyControlService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final MobileMoneyFeeRepository feeRepository;
    private final MobileMoneyFeesProperties properties;

    /** since null : contrôle indisponible (pas de date de bascule connue). */
    public record Control(Instant since, BigDecimal expected, BigDecimal gap) {
    }

    public Control control(MobileMoneyBalance balance) {
        Instant since = balance.openingDate() != null ? balance.openingDate() : properties.getLedgerStart();
        if (since == null) {
            return new Control(null, null, null);
        }
        BigDecimal opening = balance.openingDate() != null && balance.openingBalance() != null ? balance.openingBalance() : BigDecimal.ZERO;
        BigDecimal expected = opening
            .add(paymentTransactionRepository.sumConfirmedSince(PawaPayProvider.CODE, since))
            .subtract(withdrawalRepository.sumCompletedPayoutsSince(since))
            .subtract(feeRepository.sumSince(since));
        return new Control(since, expected, balance.settled().subtract(expected));
    }
}
