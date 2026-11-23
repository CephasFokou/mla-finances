package org.mla.finance.reminder;

import org.mla.finance.domain.Permission;
import org.mla.finance.domain.ReminderCase;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Alerte aux trésoriers (utilisateurs actifs ayant SEND_REMINDERS) quand un membre dit avoir payé mais que rien n'est
 * retrouvé (cadrage §4.3) : il peut s'agir d'un paiement en espèces ou par virement à saisir. Aucun montant ni contact
 * du membre dans l'email : seulement son nom usuel et un lien vers l'écran Relances.
 */
@Service
public class ReminderAlertService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String backOfficeUrl;

    public ReminderAlertService(UserRepository userRepository, EmailService emailService,
                                @Value("${app.frontend-base-url}") String backOfficeUrl) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.backOfficeUrl = backOfficeUrl.replaceAll("/+$", "");
    }

    public void declaredButNotFound(ReminderCase rc) {
        String name = rc.getDonor().getLabel();
        ReminderMotive motive = ReminderMotive.of(rc);
        String link = backOfficeUrl + "/reminders";
        String subject = "Relances : " + name + " dit avoir payé " + (motive.isProject()
            ? "sa participation à la collecte « " + motive.label() + " »" : motive.label()) + " — à vérifier";
        String html = "<p>Bonjour,</p><p><strong>" + HtmlUtils.htmlEscape(name) + "</strong> répond avoir déjà versé "
            + HtmlUtils.htmlEscape(motive.isProject() ? "sa participation à la collecte « " + motive.label() + " »"
                : "sa contribution de " + motive.label())
            + ", mais aucun versement ni aucun paiement en cours n'est enregistré.</p>"
            + "<p>S'il s'agit d'un paiement en espèces ou par virement, saisissez la contribution : le dossier sera clos "
            + "automatiquement. Sinon, sa réponse est à lire dans l'écran Relances (filtre « Non retrouvés ») :</p>"
            + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + HtmlUtils.htmlEscape(link) + "</a></p>";
        for (User user : recipients()) {
            emailService.sendHtml(user.getEmail(), subject, html, null);
        }
    }

    private List<User> recipients() {
        return userRepository.findAll().stream()
            .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getEmail() != null && !u.getEmail().isBlank())
            .filter(u -> !u.getEmail().endsWith("@systeme.mla-finance"))
            .filter(u -> u.getRoles().stream().anyMatch(r -> r.getPermissions().contains(Permission.SEND_REMINDERS)))
            .toList();
    }
}
