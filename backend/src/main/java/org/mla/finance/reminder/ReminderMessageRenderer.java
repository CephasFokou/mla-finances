package org.mla.finance.reminder;

import org.mla.finance.domain.Donor;
import org.mla.finance.domain.Project;
import org.mla.finance.reminder.whatsapp.KapsoProperties;
import org.mla.finance.reminder.whatsapp.WhatsAppTemplate;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Texte des relances (cadrage Relances §5, formulations validées le 22/11/2026). Factuel (pas de ton promotionnel) et
 * limité au seul mois visé : les mois en retard ne sont jamais cités. Email : charte ICC (violet #70358E, Montserrat /
 * Figtree), trois boutons (Contribuer maintenant, Bien reçu, C'est déjà fait). WhatsApp : modèle approuvé par Meta,
 * paramètres nommés, boutons de réponse rapide identifiés « mlaf:… ».
 */
@Component
public class ReminderMessageRenderer {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VIOLET = "#70358E";

    private final String publicSiteUrl;
    private final String templateMonthly;
    private final String templatePromise;
    private final String templateProject;

    public ReminderMessageRenderer(@Value("${app.reminders.public-site-url}") String publicSiteUrl, KapsoProperties kapso) {
        this.publicSiteUrl = publicSiteUrl.endsWith("/") ? publicSiteUrl.substring(0, publicSiteUrl.length() - 1) : publicSiteUrl;
        this.templateMonthly = kapso.getTemplateMonthly();
        this.templatePromise = kapso.getTemplatePromise();
        this.templateProject = kapso.getTemplateProject();
    }

    /**
     * Relance d'une collecte (cadrage §6 bis, modèle mla_finance_relance_collecte) : participation libre, aucun montant.
     * {@code promise} : rappel du jour promis (email propre ; sur WhatsApp, le même modèle sans « Bien reçu » n'existe
     * pas, la relance de collecte est donc réutilisée).
     */
    public ReminderMessage project(Donor donor, Project project, LocalDate due, ReminderLinks links, boolean promise) {
        String name = donor.getLabel() == null ? "" : donor.getLabel().trim();
        String collecte = project.getName().trim();
        String note = projectNote(project);
        String dueLabel = due.format(DATE);
        String contributeUrl = publicSiteUrl + "/c/" + links.contributeToken();
        String ackUrl = publicSiteUrl + "/relance/" + links.actionToken() + "?a=ack";
        String doneUrl = publicSiteUrl + "/relance/" + links.actionToken() + "?a=done";

        String subject = (promise ? "Rappel convenu — collecte « " : "Rappel — collecte « ") + collecte + " » du MLA";
        String intro = promise
            ? "Comme convenu, voici le rappel pour la collecte « " + collecte + " » (" + note + "), ouverte jusqu'au " + dueLabel + "."
            : "Petit rappel de la Coordination du MLA pour la collecte « " + collecte + " » (" + note + "), ouverte jusqu'au "
                + dueLabel + ".";
        String free = "La participation est libre : chaque don compte, quel que soit son montant.";
        String text = "Bonjour " + name + ",\n\n" + intro + "\n" + free + "\n"
            + "\nParticiper maintenant (Orange Money ou MTN MoMo) : " + contributeUrl + "\n"
            + (promise ? "" : "Bien reçu : " + ackUrl + "\n")
            + "C'est déjà fait : " + doneUrl + "\n\n"
            + "Merci pour votre soutien.\n\n"
            + "Vous recevez ce message car vous êtes membre actif du MLA.";

        String introHtml = (promise ? "Comme convenu, voici le rappel pour la collecte <strong>"
            : "Petit rappel de la Coordination du MLA pour la collecte <strong>")
            + HtmlUtils.htmlEscape(collecte) + "</strong> (" + HtmlUtils.htmlEscape(note) + "), ouverte jusqu'au <strong>"
            + dueLabel + "</strong>.";
        String html = shell(name, introHtml, "<p style=\"margin:0 0 22px;\">" + free + "</p>",
            button(contributeUrl, "Participer maintenant", true),
            (promise ? "" : button(ackUrl, "Bien reçu", false) + "&nbsp;&nbsp;") + button(doneUrl, "C'est déjà fait", false),
            "Merci pour votre soutien.");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("nom", name);
        params.put("collecte", collecte);
        params.put("message", note);
        params.put("echeance", dueLabel);
        List<String> quickReplies = List.of(ReminderPayloads.ack(links.caseId()), ReminderPayloads.done(links.caseId()));
        WhatsAppTemplate whatsapp = new WhatsAppTemplate(templateProject, params, quickReplies, links.contributeToken());
        return new ReminderMessage(subject, html, text, whatsapp);
    }

    /** Message court de l'admin ; WhatsApp refuse un paramètre vide. */
    static String projectNote(Project project) {
        String note = project.getReminderMessage();
        return note == null || note.isBlank() ? "collecte du MLA" : note.trim();
    }

    /** Relance de la contribution mensuelle (email + modèle WhatsApp mla_finance_relance_contribution). */
    public ReminderMessage monthly(Donor donor, YearMonth month, BigDecimal suggestedAmount, ReminderLinks links) {
        return render(donor, month, suggestedAmount, links, false);
    }

    /** Rappel automatique le jour promis par le membre (modèle mla_finance_rappel_promesse). */
    public ReminderMessage promise(Donor donor, YearMonth month, BigDecimal suggestedAmount, ReminderLinks links) {
        return render(donor, month, suggestedAmount, links, true);
    }

    private ReminderMessage render(Donor donor, YearMonth month, BigDecimal suggestedAmount, ReminderLinks links, boolean promise) {
        String name = donor.getLabel() == null ? "" : donor.getLabel().trim();
        String monthLabel = monthLabel(month);
        String amount = fcfa(suggestedAmount);
        String due = month.atEndOfMonth().format(DATE);
        String contributeUrl = publicSiteUrl + "/c/" + links.contributeToken();
        String ackUrl = publicSiteUrl + "/relance/" + links.actionToken() + "?a=ack";
        String doneUrl = publicSiteUrl + "/relance/" + links.actionToken() + "?a=done";

        String subject = promise
            ? "Rappel convenu — votre contribution MLA de " + monthLabel
            : "Rappel — votre contribution MLA de " + monthLabel;
        String intro = promise
            ? "Comme convenu, voici le rappel pour votre contribution mensuelle de " + monthLabel + " (" + amount + "), échéance le " + due + "."
            : "Petit rappel de la Coordination du MLA : votre contribution mensuelle de " + monthLabel + " n'a pas encore été enregistrée.";
        String text = "Bonjour " + name + ",\n\n" + intro + "\n"
            + (promise ? "" : "Montant habituel : " + amount + " — échéance : " + due + ".\n")
            + "\nContribuer maintenant (Orange Money ou MTN MoMo) : " + contributeUrl + "\n"
            + (promise ? "" : "Bien reçu : " + ackUrl + "\n")
            + "C'est déjà fait : " + doneUrl + "\n\n"
            + "Merci pour votre fidélité.\n\n"
            + "Vous recevez ce message car vous êtes membre actif du MLA.";

        String introHtml = promise
            ? "Comme convenu, voici le rappel pour votre <strong>contribution mensuelle</strong> de <strong>" + monthLabel
                + "</strong> (" + amount + "), échéance le <strong>" + due + "</strong>."
            : "Petit rappel de la Coordination du MLA : votre <strong>contribution mensuelle</strong> de <strong>" + monthLabel
                + "</strong> n'a pas encore été enregistrée.";
        String html = shell(name, introHtml,
            promise ? "" : "<p style=\"margin:0 0 22px;\">Montant habituel : <strong>" + amount + "</strong> — échéance : <strong>" + due + "</strong>.</p>",
            button(contributeUrl, "Contribuer maintenant", true),
            (promise ? "" : button(ackUrl, "Bien reçu", false) + "&nbsp;&nbsp;") + button(doneUrl, "C'est déjà fait", false),
            "Merci pour votre fidélité.");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("nom", name);
        params.put("motif", "contribution mensuelle");
        params.put("mois", monthLabel);
        params.put("montant", amount);
        params.put("echeance", due);
        List<String> quickReplies = promise
            ? List.of(ReminderPayloads.done(links.caseId()))
            : List.of(ReminderPayloads.ack(links.caseId()), ReminderPayloads.done(links.caseId()));
        WhatsAppTemplate whatsapp = new WhatsAppTemplate(promise ? templatePromise : templateMonthly, params, quickReplies,
            links.contributeToken());
        return new ReminderMessage(subject, html, text, whatsapp);
    }

    /** Gabarit email commun (charte ICC) : bandeau, salutation, introduction, boutons, pied. */
    private static String shell(String name, String introHtml, String detailHtml, String primaryButton, String secondaryButtons,
                                String thanks) {
        return "<!doctype html><html lang=\"fr\"><body style=\"margin:0;padding:0;background:#F5F2F8;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F5F2F8;padding:24px 0;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;width:100%;"
            + "background:#FFFFFF;border-radius:12px;overflow:hidden;font-family:Figtree,Helvetica,Arial,sans-serif;color:#2B2433;\">"
            + "<tr><td style=\"background:" + VIOLET + ";padding:20px 28px;font-family:Montserrat,Helvetica,Arial,sans-serif;"
            + "font-weight:700;font-size:16px;color:#FFFFFF;letter-spacing:.3px;\">MLA — ICC Yaoundé</td></tr>"
            + "<tr><td style=\"padding:28px;font-size:15px;line-height:1.6;\">"
            + "<p style=\"margin:0 0 14px;\">Bonjour " + HtmlUtils.htmlEscape(name) + ",</p>"
            + "<p style=\"margin:0 0 14px;\">" + introHtml + "</p>"
            + detailHtml
            + "<p style=\"margin:0 0 12px;\">" + primaryButton + "</p>"
            + "<p style=\"margin:0 0 22px;\">" + secondaryButtons + "</p>"
            + "<p style=\"margin:0 0 14px;color:#5E5568;font-size:14px;\">Paiement en ligne par Orange Money ou MTN MoMo.</p>"
            + "<p style=\"margin:0;\">" + thanks + "</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:16px 28px;border-top:1px solid #EEE8F3;font-size:12px;color:#8A8194;\">"
            + "Vous recevez ce message car vous êtes membre actif du MLA.</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    private static String button(String url, String label, boolean primary) {
        String style = primary
            ? "display:inline-block;background:" + VIOLET + ";color:#FFFFFF;border:2px solid " + VIOLET + ";"
            : "display:inline-block;background:#FFFFFF;color:" + VIOLET + ";border:2px solid " + VIOLET + ";";
        return "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"" + style + "text-decoration:none;"
            + "font-family:Montserrat,Helvetica,Arial,sans-serif;font-weight:600;padding:10px 20px;border-radius:8px;\">"
            + label + "</a>";
    }

    /**
     * Texte du modèle WhatsApp tel que le membre le lit (aperçu du back-office) : mêmes corps que les modèles approuvés
     * chez Meta le 22/11/2026 ; les paramètres y sont substitués, les boutons listés en fin.
     */
    public static String whatsappText(WhatsAppTemplate t) {
        if (t == null) {
            return null;
        }
        Map<String, String> p = t.bodyParams();
        if (p.containsKey("collecte")) {
            return "Bonjour " + p.get("nom") + ",\nPetit rappel de la Coordination du MLA pour la collecte *" + p.get("collecte")
                + "* (" + p.get("message") + "), ouverte jusqu'au " + p.get("echeance") + ".\nLa participation est libre : chaque "
                + "don compte, quel que soit son montant.\nVous pouvez participer en ligne en un clic avec le bouton ci-dessous "
                + "(Orange Money ou MTN MoMo).\nMerci pour votre soutien 🙏\nMLA — ICC Yaoundé\n"
                + "[Bien reçu] [C'est déjà fait] [Participer maintenant]";
        }
        String body;
        String buttons;
        if (t.quickReplyPayloads().size() == 1) {
            body = "Bonjour " + p.get("nom") + ",\nComme convenu, voici le rappel pour votre *" + p.get("motif") + "* de *"
                + p.get("mois") + "* (" + p.get("montant") + "), échéance le " + p.get("echeance") + ".\n"
                + "Vous pouvez la verser en un clic avec le bouton ci-dessous.\nMerci 🙏";
            buttons = "[C'est déjà fait] [Contribuer maintenant]";
        } else {
            body = "Bonjour " + p.get("nom") + ",\nPetit rappel de la Coordination du MLA : votre *" + p.get("motif") + "* de *"
                + p.get("mois") + "* n'a pas encore été enregistrée.\nMontant habituel : " + p.get("montant") + " — échéance : "
                + p.get("echeance") + ".\nVous pouvez contribuer en ligne en un clic avec le bouton ci-dessous (Orange Money ou "
                + "MTN MoMo).\nMerci pour votre fidélité 🙏";
            buttons = "[Bien reçu] [C'est déjà fait] [Contribuer maintenant]";
        }
        return body + "\nMLA — ICC Yaoundé\n" + buttons;
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
