package org.mla.finance.service;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Contribution;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ContributionCsvExporter {

    private static final String SEPARATOR = ";";
    private static final String HEADER =
        "Date;Mois d'attribution;Projet;Catégorie;Donateur;Montant;Canal;Type;Anonyme;Notes";

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
            text(c.getDonor() != null ? c.getDonor().getFullName() : null),
            c.getAmount().toPlainString(),
            text(c.getPaymentChannel().getLabel()),
            c.getMovementType().name(),
            c.isAnonymous() ? "Oui" : "Non",
            text(c.getNotes()));
    }

    /** Échappe un champ texte libre ; neutralise aussi les préfixes interprétés comme formules par Excel. */
    private static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(SEPARATOR) || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
