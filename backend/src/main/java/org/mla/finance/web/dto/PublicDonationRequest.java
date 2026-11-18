package org.mla.finance.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.mla.finance.domain.PaymentTransactionKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * Don public. Deux formes :
 * <ul>
 *   <li>don à une collecte (type absent ou DONATION) : projectId et amount requis, comme avant ;</li>
 *   <li>contribution mensuelle (type MONTHLY_CONTRIBUTION) : donorId (le membre), months (« YYYY-MM »,
 *       un ou plusieurs) et amountPerMonth requis ; le total payé est amountPerMonth × nombre de mois,
 *       amount, anonymous, donorName et donorContact sont ignorés.</li>
 * </ul>
 * Les champs requis selon le type sont contrôlés par PublicDonationService (HTTP 400).
 */
public record PublicDonationRequest(
    PaymentTransactionKind type,
    Long projectId,
    @NotNull Long paymentChannelId,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
    boolean anonymous,
    @Size(max = 160) String donorName,
    @Size(max = 160) String donorContact,
    /** Numéro Mobile Money à débiter, requis uniquement pour un canal payé en ligne (ex. 6xxxxxxxx ou 2376xxxxxxxx). */
    @Size(max = 20) String payerPhone,
    Long donorId,
    @Size(max = 24) List<@NotNull @Pattern(regexp = "\\d{4}-\\d{2}", message = "format attendu : AAAA-MM") String> months,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amountPerMonth
) {
    public PaymentTransactionKind kind() {
        return type == null ? PaymentTransactionKind.DONATION : type;
    }
}
