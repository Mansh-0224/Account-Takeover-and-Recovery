package com.ato.containment.service;

import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private User user;
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(RAW_PASSWORD));
    }

    @Test
    void correctPassword_onAnActiveAccount_succeedsAndResetsFailedCount() {
        user.setFailedLoginCount(3);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Optional<User> result = authService.authenticate(user.getEmail(), RAW_PASSWORD);

        assertThat(result).isPresent();
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void wrongPassword_isRejectedAndIncrementsFailedCount() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Optional<User> result = authService.authenticate(user.getEmail(), "totally-wrong-password");

        assertThat(result).isEmpty();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLastFailedLoginAt()).isNotNull();
    }

    @Test
    void unknownEmail_isRejected_sameAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        Optional<User> result = authService.authenticate("nobody@example.com", RAW_PASSWORD);

        assertThat(result).isEmpty();
    }

    @Test
    void correctPassword_onADisabledAccount_isRejected() {
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Optional<User> result = authService.authenticate(user.getEmail(), RAW_PASSWORD);

        assertThat(result).isEmpty();
    }

    @Test
    void correctPassword_onAContainedAccount_isRejected() {
        user.setStatus(UserStatus.CONTAINED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Optional<User> result = authService.authenticate(user.getEmail(), RAW_PASSWORD);

        assertThat(result).isEmpty();
    }

    @Test
    void changePassword_withWrongCurrentPassword_isRejected() {
        assertThat(catchException(() -> authService.changePassword(user, "wrong-current", "brand-new-password123")))
                .isNotNull();
    }

    @Test
    void changePassword_withCorrectCurrentPassword_updatesTheHash() {
        String oldHash = user.getPasswordHash();
        authService.changePassword(user, RAW_PASSWORD, "brand-new-password123");

        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(new BCryptPasswordEncoder().matches("brand-new-password123", user.getPasswordHash())).isTrue();
    }

    private Exception catchException(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
