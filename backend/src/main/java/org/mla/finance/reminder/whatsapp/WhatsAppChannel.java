package org.mla.finance.reminder.whatsapp;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.ReminderChannel;
import org.mla.finance.reminder.NotificationChannel;
import org.mla.finance.reminder.ReminderMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Relance par WhatsApp (modèle approuvé, RL-03). Actif seulement si Kapso est configuré. */
@Component
@RequiredArgsConstructor
public class WhatsAppChannel implements NotificationChannel {

    private final KapsoClient client;
    private final KapsoProperties properties;

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return properties.isReady();
    }

    @Override
    public Optional<String> recipientFor(Donor donor) {
        return Optional.ofNullable(normalize(donor.getPhone()));
    }

    @Override
    public Outcome send(String recipient, ReminderMessage message) {
        if (message.whatsapp() == null) {
            return new Outcome(false, null, "Aucun modèle WhatsApp pour ce message.");
        }
        KapsoClient.Result result = client.sendTemplate(recipient, message.whatsapp());
        return new Outcome(result.sent(), result.messageId(), result.error());
    }

    /** Numéro camerounais au format 237 + 9 chiffres (6…), comme le payeur PawaPay ; null si inexploitable. */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = canonical(phone);
        if (digits.length() == 9 && digits.startsWith("6")) {
            digits = "237" + digits;
        }
        return digits.length() == 12 && digits.startsWith("2376") ? digits : null;
    }

    /**
     * Même numéro, en comparant les 9 derniers chiffres (avec ou sans indicatif). WhatsApp identifie encore certains
     * comptes camerounais par l'ancien numéro à 8 chiffres (237 + 8 chiffres, sans le 6 initial) : il est d'abord
     * ramené au format actuel (6 + 8 chiffres).
     */
    public static boolean sameNumber(String a, String b) {
        String x = canonical(a);
        String y = canonical(b);
        return x.length() >= 9 && y.length() >= 9 && x.substring(x.length() - 9).equals(y.substring(y.length() - 9));
    }

    private static String canonical(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("237")) {
            digits = "2376" + digits.substring(3);
        }
        return digits;
    }
}
