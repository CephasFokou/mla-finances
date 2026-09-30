package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.exception.AuthenticationFailedException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.web.dto.ActivateAccountRequest;
import org.mla.finance.web.dto.AuthResponse;
import org.mla.finance.web.dto.CurrentUserResponse;
import org.mla.finance.web.dto.ForgotPasswordRequest;
import org.mla.finance.web.dto.LoginRequest;
import org.mla.finance.web.dto.ResetPasswordRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Authentification RG-18 : le backend émet et vérifie ses propres JWT (voir JwtService), pas de
 * fournisseur d'identité externe. Couvre le login, l'activation d'un compte invité (premier mot de
 * passe), et les DEUX parcours de réinitialisation demandés par Cephas : "mot de passe oublié"
 * en libre-service, et réinitialisation déclenchée par un super admin (adminResetPassword,
 * appelé depuis UserController) — les deux partagent le même mécanisme de jeton de
 * réinitialisation à usage unique, seul le déclencheur diffère.
 * <p>
 * forgotPassword ne renvoie jamais d'erreur ni de statut différent selon que l'email existe ou
 * non (RG implicite : ne jamais permettre l'énumération des comptes via ce endpoint public).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    /** Volontairement plus court que les 7 jours de l'invitation initiale : un lien de réinitialisation
     *  circule par email à un instant précis, une invitation doit rester valable le temps que l'invité
     *  la traite. */
    private static final long RESET_TOKEN_VALIDITY_MINUTES = 60;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new AuthenticationFailedException("Adresse email ou mot de passe incorrect."));

        if (user.getStatus() != UserStatus.ACTIVE
            || user.getPasswordHash() == null
            || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Adresse email ou mot de passe incorrect.");
        }

        user.setLastLoginAt(Instant.now());
        JwtService.IssuedToken issued = jwtService.issue(user);
        auditLogService.record("USER", user.getId(), "LOGIN", "Connexion de " + user.getFullName());
        return AuthResponse.of(issued, user);
    }

    public AuthResponse activate(ActivateAccountRequest request) {
        User user = userRepository.findByInvitationToken(request.token())
            .orElseThrow(() -> new AuthenticationFailedException("Lien d'invitation invalide ou déjà utilisé."));

        if (user.getInvitationExpiresAt() == null || user.getInvitationExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationFailedException(
                "Ce lien d'invitation a expiré. Demandez à un administrateur de vous renvoyer une invitation.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setInvitationToken(null);
        user.setInvitationExpiresAt(null);
        user.setLastLoginAt(Instant.now());

        JwtService.IssuedToken issued = jwtService.issue(user);
        auditLogService.record("USER", user.getId(), "ACTIVATE", "Compte de " + user.getFullName() + " activé");
        return AuthResponse.of(issued, user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email())
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .ifPresent(user -> {
                issueResetToken(user);
                auditLogService.record("USER", user.getId(), "FORGOT_PASSWORD",
                    "Demande de réinitialisation de mot de passe pour " + user.getFullName());
            });
        // Toujours la même réponse côté contrôleur (204), que l'email existe ou non, actif ou non.
    }

    public AuthResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
            .orElseThrow(() -> new AuthenticationFailedException("Lien de réinitialisation invalide ou déjà utilisé."));

        if (user.getPasswordResetTokenExpiresAt() == null || user.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationFailedException("Ce lien de réinitialisation a expiré. Demandez un nouveau lien.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);

        JwtService.IssuedToken issued = jwtService.issue(user);
        auditLogService.record("USER", user.getId(), "RESET_PASSWORD", "Mot de passe réinitialisé pour " + user.getFullName());
        return AuthResponse.of(issued, user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationFailedException("Session invalide."));
        return CurrentUserResponse.from(user);
    }

    /** Déclenché par un super admin (MANAGE_USERS) depuis l'écran Administration → Utilisateurs. */
    public void adminResetPassword(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + userId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException(
                "Impossible de réinitialiser le mot de passe de " + user.getFullName()
                    + " : statut actuel " + user.getStatus() + ".");
        }

        issueResetToken(user);
        auditLogService.record("USER", user.getId(), "ADMIN_RESET_PASSWORD",
            "Réinitialisation de mot de passe déclenchée par un administrateur pour " + user.getFullName());
    }

    /** Génère et envoie un nouveau jeton de réinitialisation ; ne journalise pas elle-même
     *  (le libellé d'audit dépend du déclencheur : self-service ou admin, voir les deux appelants). */
    private void issueResetToken(User user) {
        user.setPasswordResetToken(UUID.randomUUID().toString());
        user.setPasswordResetTokenExpiresAt(Instant.now().plus(RESET_TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES));
        emailService.sendPasswordResetEmail(user, user.getPasswordResetToken());
    }
}
