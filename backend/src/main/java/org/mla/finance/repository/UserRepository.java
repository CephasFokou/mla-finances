package org.mla.finance.repository;

import org.mla.finance.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** RG-18 : la connexion ne doit pas être sensible à la casse de l'email saisi. */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByInvitationToken(String invitationToken);

    Optional<User> findByPasswordResetToken(String passwordResetToken);
}
