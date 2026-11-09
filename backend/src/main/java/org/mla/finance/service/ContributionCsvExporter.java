package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mla.finance.service.CsvText.SEPARATOR;
import static org.mla.finance.service.CsvText.text;

@Component
@RequiredArgsConstructor
public class ContributionCsvExporter {

    private static final String HEADER =
        "Date;Mois d'attribution;Projet;Catégorie;Donateur;Montant;Canal;Nature;Type;Activité;Anonyme;Notes;Remarque";

    private final ContributionService contributionService;

    @Transactional(readOnly = true)
    public String export(ContributionFilter filter) {
        List<Contribution> contributions = contributionService.searchAll(filter);
        return Stream.concat(Stream.of(HEADER), contributions.stream().map(this::row))
            .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    private String row(Contribution c) {
        return String.join(SEPARATOR,
            c.getTransactionDate().toString(),
            YearMonth.from(c.getPeriodMonth()).toString(),
            text(c.getProject() != null ? c.getProject().getName() : null),
            text(c.getCategory() != null ? c.getCategory().getName() : null),
            text(c.getDonor() != null ? c.getDonor().getLabel() : null),
            c.getAmount().toPlainString(),
            text(c.getPaymentChannel().getLabel()),
            text(c.getNature().label()),
            c.getMovementType().name(),
            text(c.getActivity() != null ? c.getActivity().getName() : null),
            c.isAnonymous() ? "Oui" : "Non",
            text(c.getNotes()),
            text(c.getRemark()));
    }
}
