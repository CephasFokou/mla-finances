package org.mla.finance.reminder;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâches de fond des relances : envoi des relances mises en file hors plage horaire (RL-07), et clôture « Sans suite »
 * des dossiers des mois terminés. Désactivables par app.reminders.jobs-enabled=false (tests).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.reminders", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReminderJobs {

    private static final Logger log = LoggerFactory.getLogger(ReminderJobs.class);

    private final ReminderService reminderService;

    @Scheduled(fixedDelayString = "${app.reminders.queue-poll-ms:60000}", initialDelayString = "${app.reminders.queue-poll-ms:60000}")
    public void sendQueued() {
        try {
            int sent = reminderService.sendDueQueued();
            if (sent > 0) {
                log.info("Relances : {} relance(s) programmée(s) traitée(s)", sent);
            }
        } catch (RuntimeException e) {
            log.error("Relances : traitement de la file interrompu ({})", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Africa/Douala")
    public void closeFinishedMonths() {
        try {
            int closed = reminderService.closeFinishedMonths();
            if (closed > 0) {
                log.info("Relances : {} dossier(s) de mois terminés clos", closed);
            }
        } catch (RuntimeException e) {
            log.error("Relances : clôture des mois terminés interrompue ({})", e.getMessage(), e);
        }
    }
}
