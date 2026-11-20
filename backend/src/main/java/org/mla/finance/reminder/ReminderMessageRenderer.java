package org.mla.finance.reminder;

import org.mla.finance.domain.Donor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Texte des relances (cadrage Relances §5). Factuel (pas de ton promotionnel) et limité au seul mois visé : les
 * mois en retard ne sont jamais cités. Charte ICC : violet #70358E, Montserrat / Figtree.
 */
@Component
public class ReminderMessageRenderer {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VIOLET = "#70358E";

    private final String publicSiteUrl;

    public ReminderMessageRenderer(@Value("${app.reminders.public-site-url}") String publicSiteUrl) {
        this.publicSiteUrl = publicSiteUrl.endsWith("/") ? publicSiteUrl.substring(0, publicSiteUrl.length() - 1) : publicSiteUrl;
    }

    public ReminderMessage monthly(Donor donor, YearMonth month, BigDecimal suggestedAmount) {
        String name = donor.getLabel() == null ? "" : donor.getLabel().trim();
        String monthLabel = monthLabel(month);
        String amount = fcfa(suggestedAmount);
        String due = month.atEndOfMonth().format(DATE);
        String link = publicSiteUrl + "/";

        String subject = "Rappel — votre contribution MLA de " + monthLabel;
        String text = "Bonjour " + name + ",\n\n"
            + "Petit rappel de la Coordination du MLA : votre contribution mensuelle de " + monthLabel
            + " n'a pas encore été enregistrée.\n"
            + "Montant habituel : " + amount + " — échéance : " + due + ".\n\n"
            + "Vous pouvez contribuer en ligne (Orange Money ou MTN MoMo) : " + link + "\n"
            + "Si vous avez déjà versé votre contribution, merci de ne pas tenir compte de ce message.\n\n"
            + "Merci pour votre fidélité.\n\n"
            + "Vous recevez ce message car vous êtes membre actif du MLA.";

        String safeName = HtmlUtils.htmlEscape(name);
        String html = "<!doctype html><html lang=\"fr\"><body style=\"margin:0;padding:0;background:#F5F2F8;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F5F2F8;padding:24px 0;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;width:100%;"
            + "background:#FFFFFF;border-radius:12px;overflow:hidden;font-family:Figtree,Helvetica,Arial,sans-serif;color:#2B2433;\">"
            + "<tr><td style=\"background:" + VIOLET + ";padding:20px 28px;font-family:Montserrat,Helvetica,Arial,sans-serif;"
            + "font-weight:700;font-size:16px;color:#FFFFFF;letter-spacing:.3px;\">MLA — ICC Yaoundé</td></tr>"
            + "<tr><td style=\"padding:28px;font-size:15px;line-height:1.6;\">"
            + "<p style=\"margin:0 0 14px;\">Bonjour " + safeName + ",</p>"
            + "<p style=\"margin:0 0 14px;\">Petit rappel de la Coordination du MLA : votre <strong>contribution mensuelle</strong> de "
            + "<strong>" + monthLabel + "</strong> n'a pas encore été enregistrée.</p>"
            + "<p style=\"margin:0 0 22px;\">Montant habituel : <strong>" + amount + "</strong> — échéance : <strong>" + due + "</strong>.</p>"
            + "<p style=\"margin:0 0 22px;\"><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;background:" + VIOLET
            + ";color:#FFFFFF;text-decoration:none;font-family:Montserrat,Helvetica,Arial,sans-serif;font-weight:600;"
            + "padding:12px 22px;border-radius:8px;\">Contribuer maintenant</a></p>"
            + "<p style=\"margin:0 0 14px;color:#5E5568;font-size:14px;\">Orange Money ou MTN MoMo. Si vous avez déjà versé votre "
            + "contribution, merci de ne pas tenir compte de ce message.</p>"
            + "<p style=\"margin:0;\">Merci pour votre fidélité.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:16px 28px;border-top:1px solid #EEE8F3;font-size:12px;color:#8A8194;\">"
            + "Vous recevez ce message car vous êtes membre actif du MLA.</td></tr>"
            + "</table></td></tr></table></body></html>";
        return new ReminderMessage(subject, html, text);
    }

    static String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, FR) + " " + month.getYear();
    }

    static String fcfa(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(FR);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(amount == null ? BigDecimal.ZERO : amount) + " FCFA";
    }

    static String date(LocalDate date) {
        return date.format(DATE);
    }
}
