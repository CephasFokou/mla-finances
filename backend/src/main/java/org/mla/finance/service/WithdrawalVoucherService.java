package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.PayoutStatus;
import org.mla.finance.domain.User;
import org.mla.finance.domain.Withdrawal;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.payment.pawapay.PayoutOutcome;
import org.mla.finance.payment.payout.MsisdnMask;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.voucher.AmountInWords;
import org.mla.finance.voucher.VoucherPdfRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bon de décaissement PDF d'un retrait PROCESSED, produit à la demande (rien n'est stocké). Toute
 * personne qui peut voir le retrait peut le télécharger ; chaque téléchargement est tracé
 * (WITHDRAWAL_VOUCHER_DOWNLOADED). Le bon ne contient aucun numéro complet ni secret : numéro
 * Mobile Money masqué, identifiants techniques seulement.
 * <p>
 * Payout décaissé avant le stockage de la référence opérateur : elle est relue une fois chez
 * wallet-ledger (hors transaction, l'appel réseau ne se fait jamais sous verrou) puis enregistrée.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalVoucherService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalVoucherService.class);
    private static final ZoneId DOUALA = ZoneId.of("Africa/Douala");
    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", FR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", FR);
    private static final Map<String, String> OPERATORS = Map.of("ORANGE_CMR", "Orange Money", "MTN_MOMO_CMR", "MTN MoMo");

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalPayoutStateService payoutStateService;
    private final PawaPayClient pawaPayClient;
    private final PawaPayProperties pawaPayProperties;
    private final AuditLogService auditLogService;
    private final VoucherPdfRenderer renderer;
    private final PlatformTransactionManager transactionManager;

    public record Voucher(String filename, byte[] pdf) {
    }

    public Voucher generate(Long id) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Withdrawal current = tx.execute(s -> requireProcessed(id));
        if (current.getPayoutStatus() == PayoutStatus.COMPLETED && current.getPayoutProviderRef() == null
            && current.getPayoutId() != null && pawaPayProperties.isEnabled()) {
            refreshProviderRef(current);
        }
        Instant editedAt = Instant.now();
        String[] page = tx.execute(s -> {
            Withdrawal w = requireProcessed(id);
            auditLogService.record("WITHDRAWAL", id, "WITHDRAWAL_VOUCHER_DOWNLOADED",
                "Bon de décaissement du retrait #" + id + " téléchargé");
            return new String[] {filename(w), html(w, editedAt)};
        });
        return new Voucher(page[0], renderer.render(page[1]));
    }

    private void refreshProviderRef(Withdrawal w) {
        PayoutOutcome outcome = pawaPayClient.checkPayout(w.getPayoutId());
        if (outcome.kind() == PayoutOutcome.Kind.COMPLETED && outcome.providerTransactionId() != null) {
            payoutStateService.storeProviderRef(w.getId(), outcome.providerTransactionId());
        } else {
            log.info("Référence opérateur du payout {} non disponible chez wallet-ledger ({})", w.getPayoutId(), outcome.kind());
        }
    }

    private Withdrawal requireProcessed(Long id) {
        Withdrawal w = withdrawalRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Retrait introuvable : " + id));
        if (w.getStatus() != WithdrawalStatus.PROCESSED) {
            throw new ConflictException("Le bon de décaissement n'existe que pour un retrait décaissé (retrait " + id
                + " : statut " + w.getStatus() + ").");
        }
        // Initialise les associations paresseuses tant que la session est ouverte.
        w.getRequestedBy();
        return w;
    }

    static String referenceOf(Withdrawal w) {
        return w.getReference() != null && !w.getReference().isBlank() ? w.getReference().trim() : "RET-" + w.getId();
    }

    static String filename(Withdrawal w) {
        return "bon-decaissement-" + referenceOf(w).replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf";
    }

    // ------------------------------------------------------------------ gabarit

    String html(Withdrawal w, Instant editedAt) {
        boolean mobileMoney = w.getPayoutStatus() == PayoutStatus.COMPLETED;
        List<String[]> details = new ArrayList<>();
        details.add(row("Enveloppe", envelope(w)));
        details.add(row("Motif", w.getReason()));
        details.add(row("Nature", w.getNature() != null ? w.getNature().label() : null));
        if (w.getPerson() != null) {
            details.add(row("Membre / Tiers", w.getPerson().getLabel()));
        }
        if (w.getActivity() != null) {
            details.add(row("Activité", w.getActivity().getName()));
        }
        if (w.getRemark() != null && !w.getRemark().isBlank()) {
            details.add(row("Remarque", w.getRemark()));
        }

        List<String[]> circuit = new ArrayList<>();
        circuit.add(row("Saisi par", who(w.getRequestedBy(), w.getRequestedAt())));
        circuit.add(row("Validé par", who(w.getDecidedBy(), w.getDecidedAt())));
        circuit.add(row("Décaissé le", disbursedOn(w)));
        circuit.add(row("Canal", channel(w, mobileMoney)));

        StringBuilder b = new StringBuilder(8000);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"fr\"><head>")
            .append("<title>Bon de décaissement ").append(esc(referenceOf(w))).append("</title>")
            .append("<meta name=\"author\" content=\"MLA Finance\"/>")
            .append("<style>").append(CSS).append("</style></head><body>");
        b.append("<div class=\"foot\">Document généré par MLA Finance le ")
            .append(esc(DATE_TIME.format(editedAt.atZone(DOUALA))))
            .append(" (heure de Yaoundé). Pour toute question, contactez le secrétariat du MLA en indiquant la référence.</div>");

        b.append("<div class=\"ribbon\"><span class=\"r1\"></span><span class=\"r2\"></span><span class=\"r3\"></span>")
            .append("<span class=\"r4\"></span><span class=\"r5\"></span></div>");
        b.append("<table class=\"head\"><tr><td class=\"logo\"><img src=\"").append(renderer.logoDataUri())
            .append("\" alt=\"ICC\"/></td><td><div class=\"org\">MLA, Impact Centre Chrétien Yaoundé</div>")
            .append("<div class=\"sub\">MLA Finance · Trésorerie</div></td>")
            .append("<td class=\"edited\">Édité le<br/><strong>").append(esc(DATE.format(editedAt.atZone(DOUALA))))
            .append("</strong></td></tr></table>");

        b.append("<h1>Bon de décaissement</h1>")
            .append("<div class=\"ref\">Référence <strong>").append(esc(referenceOf(w))).append("</strong>")
            .append(" · Retrait n° ").append(w.getId()).append("</div>");

        b.append("<div class=\"amount\"><div class=\"label\">Montant décaissé</div><div class=\"figures\">")
            .append(esc(fcfa(w.getAmount()))).append("</div><div class=\"words\">")
            .append(esc(AmountInWords.fcfa(w.getAmount()))).append("</div></div>");

        if (w.isSelfApproved()) {
            b.append("<div class=\"flag\">").append(esc(selfApprovedMention(w))).append("</div>");
        }

        section(b, "Objet", details);
        section(b, "Circuit", circuit);

        if (mobileMoney) {
            List<String[]> mm = new ArrayList<>();
            mm.add(row("Opérateur", OPERATORS.getOrDefault(w.getPayoutProvider(), w.getPayoutProvider())));
            mm.add(row("Numéro", MsisdnMask.mask(w.getPayoutPhone())));
            mm.add(row("Envoyé le", w.getPayoutCompletedAt() != null ? DATE_TIME.format(w.getPayoutCompletedAt().atZone(DOUALA)) : null));
            mm.add(row("Identifiant du payout", w.getPayoutId()));
            mm.add(row("Référence opérateur", w.getPayoutProviderRef() != null ? w.getPayoutProviderRef()
                : "Non communiquée par l'opérateur"));
            section(b, "Décaissement Mobile Money", mm);
            b.append("<p class=\"note\">Paiement envoyé par MLA Finance via la passerelle Mobile Money, ")
                .append("confirmé par l'opérateur : ce bon tient lieu de justificatif, sans signature.</p>");
        } else {
            b.append("<h2>Signatures</h2><table class=\"signatures\"><tr>")
                .append("<td><div class=\"role\">Demandeur</div><div class=\"name\">").append(esc(name(w.getRequestedBy()))).append("</div></td>")
                .append("<td><div class=\"role\">Trésorier</div><div class=\"name\"> </div></td>")
                .append("<td><div class=\"role\">Bénéficiaire</div><div class=\"name\">")
                .append(esc(w.getPerson() != null ? w.getPerson().getLabel() : " ")).append("</div></td>")
                .append("</tr></table>");
        }

        b.append("</body></html>");
        return b.toString();
    }

    private static String selfApprovedMention(Withdrawal w) {
        return WithdrawalService.isSuperAdmin(w.getRequestedBy())
            ? "Auto-validé par un super administrateur : saisi et validé par la même personne."
            : "Saisi et validé par la même personne.";
    }

    private static void section(StringBuilder b, String title, List<String[]> rows) {
        b.append("<h2>").append(esc(title)).append("</h2><table class=\"rows\">");
        for (String[] r : rows) {
            b.append("<tr><th>").append(esc(r[0])).append("</th><td>").append(esc(r[1] != null ? r[1] : "—")).append("</td></tr>");
        }
        b.append("</table>");
    }

    private static String[] row(String label, String value) {
        return new String[] {label, value};
    }

    private static String envelope(Withdrawal w) {
        if (w.getProject() != null) {
            return "Projet · " + w.getProject().getName();
        }
        return w.getCategory() != null ? "Caisse · " + w.getCategory().getName() : null;
    }

    private static String who(User user, Instant at) {
        String date = at != null ? "le " + DATE_TIME.format(at.atZone(DOUALA)) : null;
        if (user == null) {
            return date;
        }
        return date != null ? user.getFullName() + ", " + date : user.getFullName();
    }

    private static String name(User user) {
        return user != null ? user.getFullName() : " ";
    }

    private static String disbursedOn(Withdrawal w) {
        LocalDate date = w.getOperationDate();
        if (date != null) {
            return DATE.format(date);
        }
        return w.getProcessedAt() != null ? DATE.format(w.getProcessedAt().atZone(DOUALA)) : null;
    }

    private static String channel(Withdrawal w, boolean mobileMoney) {
        if (w.getPaymentChannel() != null) {
            return w.getPaymentChannel().getLabel();
        }
        return mobileMoney ? OPERATORS.getOrDefault(w.getPayoutProvider(), "Mobile Money") : null;
    }

    static String fcfa(BigDecimal amount) {
        NumberFormat f = NumberFormat.getIntegerInstance(FR);
        // Espaces insécables du format français (U+202F, U+00A0) remplacées par une espace simple.
        return f.format(amount.setScale(0, java.math.RoundingMode.HALF_UP)).replace('\u202F', ' ').replace('\u00A0', ' ') + " FCFA";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t') {
                        continue;
                    }
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final String CSS = """
        @page { size: A4 portrait; margin: 14mm 16mm 22mm 16mm;
          @bottom-center { content: element(foot); } }
        body { font-family: 'MLA Sans'; font-size: 10pt; color: #1f1a24; line-height: 1.35; }
        .ribbon { height: 4px; margin-bottom: 10px; }
        .ribbon span { display: inline-block; width: 20%; height: 4px; }
        .r1 { background: #70358e; } .r2 { background: #16a5da; } .r3 { background: #df3a3a; }
        .r4 { background: #72bf5b; } .r5 { background: #f9d03b; }
        table { border-collapse: collapse; width: 100%; }
        .head td { vertical-align: middle; }
        .head .logo { width: 52px; }
        .head .logo img { width: 44px; }
        .org { font-weight: 700; font-size: 12.5pt; color: #3a065b; }
        .sub { font-size: 8.5pt; color: #6b6372; }
        .edited { text-align: right; font-size: 8.5pt; color: #6b6372; width: 130px; }
        .edited strong { color: #1f1a24; font-size: 9.5pt; }
        h1 { font-size: 20pt; color: #5f148b; margin: 16px 0 2px 0; font-weight: 700; }
        .ref { font-size: 9.5pt; color: #6b6372; margin-bottom: 12px; }
        .ref strong { color: #1f1a24; }
        .amount { background: #f3ecf8; border: 1px solid #dccbe8; border-left: 5px solid #70358e;
          padding: 10px 14px; margin-bottom: 10px; }
        .amount .label { font-size: 8.5pt; text-transform: uppercase; letter-spacing: 0.6px; color: #5f148b; }
        .amount .figures { font-size: 22pt; font-weight: 700; color: #3a065b; }
        .amount .words { font-size: 10.5pt; color: #3a065b; }
        .flag { background: #fdf5d3; border: 1px solid #f9d03b; color: #6b5200; font-weight: 700;
          padding: 6px 10px; margin-bottom: 8px; font-size: 9.5pt; }
        h2 { font-size: 10pt; text-transform: uppercase; letter-spacing: 0.6px; color: #5f148b;
          border-bottom: 1px solid #dccbe8; padding-bottom: 3px; margin: 12px 0 4px 0; }
        .rows th { text-align: left; font-weight: 400; color: #6b6372; width: 34%; padding: 3px 8px 3px 0;
          vertical-align: top; }
        .rows td { padding: 3px 0; vertical-align: top; word-wrap: break-word; }
        .note { font-size: 8.5pt; color: #6b6372; margin-top: 8px; }
        .signatures td { width: 33.3%; border: 1px solid #dccbe8; height: 80px; vertical-align: top; padding: 6px 8px; }
        .signatures .role { font-size: 8.5pt; text-transform: uppercase; letter-spacing: 0.5px; color: #5f148b; }
        .signatures .name { font-size: 9pt; margin-top: 2px; }
        .foot { position: running(foot); font-size: 7.5pt; color: #6b6372; text-align: center;
          border-top: 1px solid #dccbe8; padding-top: 4px; }
        """;
}
