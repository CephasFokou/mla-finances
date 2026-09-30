package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Role;
import org.mla.finance.domain.User;
import org.mla.finance.domain.UserStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.RoleRepository;
import org.mla.finance.repository.UserRepository;
import org.mla.finance.web.dto.UserInviteRequest;
import org.mla.finance.web.dto.UserResponse;
import org.mla.finance.web.dto.UserRolesUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des comptes du back-office. Comme documenté sur l'entité {@code User} : aucun mot de
 * passe n'est saisi par l'administrateur, un compte est créé par invitation (jeton à usage unique,
 * durée de vie 7 jours), envoyée par email via {@link EmailService} (Resend, RG-18). Le jeton
 * reste néanmoins renvoyé dans {@code UserResponse} tant que le compte est PENDING_INVITATION,
 * pour permettre de le copier/transmettre manuellement en secours si l'email n'arrive pas.
 *
 * Le parcours "l'invité accepte son invitation et devient ACTIVE" est désormais exposé — voir
 * {@link AuthService#activate}, qui consomme invitationToken et fixe le premier mot de passe.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final long INVITATION_VALIDITY_DAYS = 7;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    public UserResponse invite(UserInviteRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("Un utilisateur avec l'adresse '" + request.email() + "' existe déjà.");
        }
        Set<Role> roles = resolveRoles(request.roleIds());

        User user = User.builder()
            .email(request.email())
            .fullName(request.fullName())
            .status(UserStatus.PENDING_INVITATION)
            .invitationToken(UUID.randomUUID().toString())
            .invitationExpiresAt(Instant.now().plus(INVITATION_VALIDITY_DAYS, ChronoUnit.DAYS))
            .roles(roles)
            .build();
        User saved = userRepository.save(user);
        emailService.sendInvitationEmail(saved, saved.getInvitationToken());
        auditLogService.record("USER", saved.getId(), "INVITE",
            "Invitation envoyée à " + saved.getFullName() + " (" + saved.getEmail() + ")");
        return UserResponse.from(saved);
    }

    public UserResponse updateRoles(Long id, UserRolesUpdateRequest request) {
        User user = find(id);
        Set<Role> roles = resolveRoles(request.roleIds());
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        auditLogService.record("USER", id, "UPDATE_ROLES",
            "Rôles de " + user.getFullName() + " mis à jour (" + roles.size() + " rôle(s))");
        return UserResponse.from(user);
    }

    public UserResponse resendInvitation(Long id) {
        User user = find(id);
        if (user.getStatus() != UserStatus.PENDING_INVITATION) {
            throw new ConflictException(
                "Impossible de renvoyer une invitation à " + user.getFullName() + " : statut actuel " + user.getStatus() + ".");
        }
        user.setInvitationToken(UUID.randomUUID().toString());
        user.setInvitationExpiresAt(Instant.now().plus(INVITATION_VALIDITY_DAYS, ChronoUnit.DAYS));
        emailService.sendInvitationEmail(user, user.getInvitationToken());
        auditLogService.record("USER", id, "RESEND_INVITATION", "Invitation renvoyée à " + user.getFullName());
        return UserResponse.from(user);
    }

    /** Révoque l'accès (ACTIVE -> SUSPENDED) ou annule une invitation en attente (PENDING_INVITATION -> SUSPENDED). */
    public UserResponse suspend(Long id) {
        User user = find(id);
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ConflictException("Le compte de " + user.getFullName() + " est déjà suspendu.");
        }
        user.setStatus(UserStatus.SUSPENDED);
        auditLogService.record("USER", id, "SUSPEND", "Compte de " + user.getFullName() + " suspendu");
        return UserResponse.from(user);
    }

    public UserResponse reactivate(Long id) {
        User user = find(id);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new BusinessRuleViolationException("USER_LIFECYCLE",
                "Seul un compte suspendu peut être réactivé (statut actuel : " + user.getStatus() + ").");
        }
        user.setStatus(UserStatus.ACTIVE);
        auditLogService.record("USER", id, "REACTIVATE", "Compte de " + user.getFullName() + " réactivé");
        return UserResponse.from(user);
    }

    private Set<Role> resolveRoles(List<Long> roleIds) {
        Set<Role> roles = new HashSet<>();
        for (Long roleId : roleIds) {
            roles.add(roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rôle introuvable : " + roleId)));
        }
        return roles;
    }

    private User find(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + id));
    }
}
