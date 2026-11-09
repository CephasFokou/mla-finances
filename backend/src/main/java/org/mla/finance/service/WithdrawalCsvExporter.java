package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Withdrawal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mla.finance.service.CsvText.SEPARATOR;
import static org.mla.finance.service.CsvText.text;

/** Export CSV des retraits, sur le modèle de celui des contributions. */
@Component
@RequiredArgsConstructor
public class WithdrawalCsvExporter {

    private static final String HEADER =
        "Date de sortie;Date de demande;Projet;Catégorie;Membre / Tiers;Montant;Canal;Nature;Activité;Statut;Motif;Remarque;Référence";

    private final WithdrawalService withdrawalService;

    @Transactional(readOnly = true)
    public String export(WithdrawalFilter filter) {
        List<Withdrawal> withdrawals = withdrawalService.searchAll(filter);
        return Stream.concat(Stream.of(HEADER), withdrawals.stream().map(this::row))
            .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    private String row(Withdrawal w) {
        return String.join(SEPARATOR,
            w.getOperationDate() != null ? w.getOperationDate().toString() : "",
            w.getRequestedAt().atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            text(w.getProject() != null ? w.getProject().getName() : null),
            text(w.getCategory() != null ? w.getCategory().getName() : null),
            text(w.getPerson() != null ? w.getPerson().getLabel() : null),
            w.getAmount().toPlainString(),
            text(w.getPaymentChannel() != null ? w.getPaymentChannel().getLabel() : null),
            text(w.getNature().label()),
            text(w.getActivity() != null ? w.getActivity().getName() : null),
            w.getStatus().name(),
            text(w.getReason()),
            text(w.getRemark()),
            text(w.getReference()));
    }
}
