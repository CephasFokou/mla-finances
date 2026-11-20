package org.mla.finance.reminder;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.service.EmailService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/** Relance par email via Resend (EmailService). Toujours proposé : sans clé Resend, l'envoi est tracé en échec. */
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailService emailService;

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<String> recipientFor(Donor donor) {
        return emailOf(donor);
    }

    /** Email exploitable du membre (forme valide), vide sinon. */
    public static Optional<String> emailOf(Donor donor) {
        String email = donor.getEmail();
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            return Optional.empty();
        }
        return Optional.of(email.trim());
    }

    @Override
    public Outcome send(String recipient, ReminderMessage message) {
        EmailService.SendResult result = emailService.sendHtml(recipient, message.subject(), message.html(), message.text());
        return new Outcome(result.sent(), result.messageId(), result.error());
    }

    /** « jean.dupont@gmail.com » → « j•••@gmail.com » : jamais d'adresse complète à l'écran ni dans les journaux. */
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "•••";
        }
        return email.charAt(0) + "•••" + email.substring(at);
    }
}
