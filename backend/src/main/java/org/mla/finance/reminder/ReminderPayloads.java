package org.mla.finance.reminder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Identifiants des boutons et listes WhatsApp de MLA Finance, tous préfixés « mlaf: » : c'est ce préfixe qui les
 * distingue de ceux du suivi des tâches MLA, qui partage le même numéro (et ignore donc ces réponses).
 */
public final class ReminderPayloads {

    public static final String PREFIX = "mlaf:";

    private ReminderPayloads() {
    }

    public static String ack(Long caseId) {
        return PREFIX + "ack:" + caseId;
    }

    public static String done(Long caseId) {
        return PREFIX + "done:" + caseId;
    }

    public static String unsure(Long caseId) {
        return PREFIX + "unsure:" + caseId;
    }

    public static String date(Long caseId, LocalDate date) {
        return PREFIX + "date:" + caseId + ":" + date;
    }

    /** Action décodée d'un identifiant « mlaf:… » ; vide si l'identifiant n'est pas reconnu. */
    public record Parsed(String action, Long caseId, LocalDate date) {
    }

    public static Optional<Parsed> parse(String payload) {
        if (payload == null || !payload.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = payload.substring(PREFIX.length()).split(":");
        try {
            if (parts.length == 2 && (parts[0].equals("ack") || parts[0].equals("done") || parts[0].equals("unsure"))) {
                return Optional.of(new Parsed(parts[0], Long.valueOf(parts[1]), null));
            }
            if (parts.length == 3 && parts[0].equals("date")) {
                return Optional.of(new Parsed("date", Long.valueOf(parts[1]), LocalDate.parse(parts[2])));
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
