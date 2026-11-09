package org.mla.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mode « migration seule » ({@code --app.migrate-only=true}) : le contexte démarre (Liquibase
 * applique les migrations, les seeders de profil s'exécutent), puis l'application s'arrête au lieu
 * de servir l'API. Utilisé par scripts/reset-local-db.sh avant de charger les données importées.
 */
@Component
@Order(Integer.MAX_VALUE)
@ConditionalOnProperty(name = "app.migrate-only", havingValue = "true")
public class MigrateOnlyRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateOnlyRunner.class);

    private final ApplicationContext context;

    public MigrateOnlyRunner(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) {
        log.info("Migrations appliquées (app.migrate-only=true) : arrêt de l'application.");
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
