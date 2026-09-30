package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.service.AuthService;
import org.mla.finance.service.UserService;
import org.mla.finance.web.dto.UserInviteRequest;
import org.mla.finance.web.dto.UserResponse;
import org.mla.finance.web.dto.UserRolesUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<UserResponse> invite(@Valid @RequestBody UserInviteRequest request) {
        UserResponse created = userService.invite(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserResponse updateRoles(@PathVariable Long id, @Valid @RequestBody UserRolesUpdateRequest request) {
        return userService.updateRoles(id, request);
    }

    @PostMapping("/{id}/resend-invitation")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserResponse resendInvitation(@PathVariable Long id) {
        return userService.resendInvitation(id);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserResponse suspend(@PathVariable Long id) {
        return userService.suspend(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserResponse reactivate(@PathVariable Long id) {
        return userService.reactivate(id);
    }

    /** RG-18 : un super admin peut déclencher une réinitialisation de mot de passe pour un compte
     *  ACTIVE (ex. utilisateur bloqué qui a perdu l'accès à sa boîte mail d'invitation, ou qui
     *  préfère passer par un administrateur plutôt que par "mot de passe oublié"). */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        authService.adminResetPassword(id);
        return ResponseEntity.noContent().build();
    }
}
