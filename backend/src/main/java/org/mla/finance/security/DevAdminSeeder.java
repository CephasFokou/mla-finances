package org.mla.finance.security;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Compte SUPER_ADMIN de développement, pour pouvoir se connecter localement dès le démarrage
 * sans passer par le cycle invitation → email → activation. Volontairement un
 * {@link CommandLineRunner} conditionné par {@code @Profile("dev")}, PAS une migration Liquibase :
 * contrairement aux migrations (qui s'exécutent dans tous les profils, y compris prod),
 * un utilisateur avec un mot de passe fixe et connu ne doit jamais pouvoir exister en base de
 * production — voir claude/module-authentification-spec.md pour cette décision.
 * <p>
 * Identifiants : admin@mla-finance.dev / admin1234 (loggés au démarrage pour rappel).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);
    private static final String DEV_ADMIN_EMAIL = "admin@mla-finance.dev";
    private static final String DEV_ADMIN_PASSWORD = "admin1234";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmailIgnoreCase(DEV_ADMIN_EMAIL).isPresent()) {
            return;
        }
        Role superAdmin = roleRepository.findByName("SUPER_ADMIN")
            .orElseThrow(() -> new IllegalStateException(
                "Rôle SUPER_ADMIN introuvable — 0002-seed-reference-data.xml a-t-il bien été exécuté ?"));

        User admin = User.builder()
            .email(DEV_ADMIN_EMAIL)
            .fullName("Administrateur (dev)")
            .status(UserStatus.ACTIVE)
            .passwordHash(passwordEncoder.encode(DEV_ADMIN_PASSWORD))
            .roles(Set.of(superAdmin))
            .build();
        userRepository.save(admin);

        log.info("Compte SUPER_ADMIN de développement créé — email: {} / mot de passe: {}",
            DEV_ADMIN_EMAIL, DEV_ADMIN_PASSWORD);
    }
}
