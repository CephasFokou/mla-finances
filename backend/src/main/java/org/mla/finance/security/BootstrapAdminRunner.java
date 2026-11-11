package org.mla.finance.security;

import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Amorçage du premier compte SUPER_ADMIN sur un environnement déployé (profil prod), où
 * {@link DevAdminSeeder} n'existe pas et où aucun utilisateur ne peut être invité tant qu'aucun
 * administrateur n'existe (voir claude/plan-de-deploiement.md, étape 7).
 * <p>
 * Ne fait quelque chose que si les DEUX conditions sont réunies : la table users est vide, ET les
 * variables BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD sont renseignées. Dès qu'un
 * utilisateur existe, le runner ne touche plus à rien — les variables peuvent (et doivent) être
 * retirées de la configuration après la première connexion, et le mot de passe changé via le
 * parcours « mot de passe oublié ».
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class BootstrapAdminRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-name:Administrateur}")
    private String adminName;

    @Override
    @Transactional
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (userRepository.count() > 0) {
            log.warn("BOOTSTRAP_ADMIN_* est encore défini alors que des utilisateurs existent déjà : "
                + "ces variables peuvent être retirées de la configuration.");
            return;
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD doit faire au moins 12 caractères.");
        }
        Role superAdmin = roleRepository.findByName("SUPER_ADMIN")
            .orElseThrow(() -> new IllegalStateException(
                "Rôle SUPER_ADMIN introuvable — 0002-seed-reference-data.xml a-t-il bien été exécuté ?"));

        User admin = User.builder()
            .email(adminEmail.trim().toLowerCase())
            .fullName(adminName)
            .status(UserStatus.ACTIVE)
            .passwordHash(passwordEncoder.encode(adminPassword))
            .roles(Set.of(superAdmin))
            .build();
        userRepository.save(admin);

        log.info("Premier compte SUPER_ADMIN créé pour {} — retirez BOOTSTRAP_ADMIN_EMAIL/PASSWORD "
            + "de la configuration après la première connexion.", admin.getEmail());
    }
}
