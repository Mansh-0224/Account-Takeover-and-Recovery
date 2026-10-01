package com.ato.containment.service;

import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Verifies login attempts and handles password changes. Does not know about
 * sessions or tokens — AuthController decides what to do once it knows
 * whether the credentials were correct.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Checks an email/password pair and returns the user if they're correct
     * AND the account is allowed to sign in right now.
     *
     * Deliberately gives the same generic failure for three different
     * reasons (unknown email, wrong password, account not ACTIVE) so a
     * caller can never use the error to enumerate which emails exist or
     * which accounts are contained/disabled. Every attempt against a real
     * account updates its failed/succeeded login bookkeeping either way.
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return Optional.empty();
        }

        Optional<User> match = userRepository.findByEmailIgnoreCase(email);
        if (match.isEmpty()) {
            log.info("Login failed: no account for the given email.");
            return Optional.empty();
        }

        User user = match.get();
        boolean passwordOk = passwordEncoder.matches(rawPassword, user.getPasswordHash());

        if (!passwordOk) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            user.setLastFailedLoginAt(Instant.now());
            userRepository.save(user);
            log.info("Login failed: wrong password. userId={} failedLoginCount={}", user.getId(), user.getFailedLoginCount());
            return Optional.empty();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Login blocked: account not active. userId={} status={}", user.getId(), user.getStatus());
            return Optional.empty();
        }

        user.setFailedLoginCount(0);
        userRepository.save(user);
        return Optional.of(user);
    }

    /**
     * Changes a user's password after verifying their current one. Callers
     * are responsible for deciding what happens to existing sessions
     * afterwards (see AuthController#changePassword, which revokes all
     * other sessions).
     */
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed. userId={}", user.getId());
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
