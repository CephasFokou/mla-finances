package org.mla.finance.service;

import org.mla.finance.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Envoi des emails transactionnels (invitation, réinitialisation de mot de passe) via l'API
 * HTTPS de Resend — même fournisseur que celui déjà utilisé et validé dans le projet PÉNIEL de
 * Cephas, réutilisé ici tel quel plutôt que de rebrancher une nouvelle intégration (Brevo avait
 * été écarté faute d'être branché, voir claude/module-administration-spec.md).
 * <p>
 * Appel direct via {@link RestClient} (déjà fourni par spring-boot-starter-web) plutôt qu'un SDK
 * Resend dédié : cet environnement de développement cloud n'a pas accès à Maven Central (voir
 * phase1-analyse-excel-et-architecture-cible.md), donc la résolvabilité d'une toute nouvelle
 * dépendance ne peut pas y être vérifiée. L'API REST de Resend est simple (un seul endpoint HTTP),
 * ce qui rend cette approche largement suffisante.
 * <p>
 * Si RESEND_API_KEY n'est pas configurée (ex. environnement de démo sans clé), l'envoi est
 * silencieusement ignoré (avec un log d'avertissement) plutôt que de faire échouer la mutation
 * métier qui l'a déclenché (invitation, réinitialisation) : le jeton est de toute façon déjà
 * persisté en base, l'email n'est qu'un moyen de le transmettre.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(
        @Value("${app.email.resend-api-key:}") String apiKey,
        @Value("${app.email.from}") String from,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
        this.restClient = RestClient.builder().baseUrl("https://api.resend.com").build();
    }

    public void sendInvitationEmail(User user, String invitationToken) {
        String link = frontendBaseUrl + "/activate?token=" + invitationToken;
        send(
            user.getEmail(),
            "Invitation — MLA Finance",
            "<p>Bonjour " + user.getFullName() + ",</p>"
                + "<p>Vous êtes invité(e) à rejoindre l'espace de gestion financière MLA. "
                + "Activez votre compte et choisissez votre mot de passe :</p>"
                + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>Ce lien expire dans 7 jours.</p>"
        );
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + resetToken;
        send(
            user.getEmail(),
            "Réinitialisation de votre mot de passe — MLA Finance",
            "<p>Bonjour " + user.getFullName() + ",</p>"
                + "<p>Une réinitialisation de mot de passe a été demandée pour votre compte. "
                + "Choisissez un nouveau mot de passe :</p>"
                + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>Ce lien expire dans 1 heure. Si vous n'êtes pas à l'origine de cette demande, "
                + "ignorez cet email : votre mot de passe actuel reste valide.</p>"
        );
    }

    private void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.email.resend-api-key non configurée — email non envoyé (destinataire={}, sujet={})", to, subject);
            return;
        }
        try {
            restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "html", html
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Échec d'envoi d'email via Resend (destinataire={}, sujet={})", to, subject, e);
        }
    }
}
