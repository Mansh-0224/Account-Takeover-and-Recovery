package com.ato.containment.service;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Role;
import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
    }

    @Test
    void create_neverStoresTheRawToken() {
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        when(sessionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        SessionService.NewSession created = sessionService.create(user, "device-1", "1.2.3.4", null, "Mozilla/5.0");

        Session saved = captor.getValue();
        assertThat(saved.getTokenHash()).isNotEqualTo(created.rawToken());
        assertThat(saved.getTokenHash()).doesNotContain(created.rawToken());
        assertThat(saved.getTokenHash()).hasSize(64); // hex-encoded SHA-256
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(saved.getTenantId()).isEqualTo(user.getTenant().getId());
    }

    @Test
    void resolve_withTheCorrectRawToken_findsTheSession() {
        Session stored = activeSessionExpiringIn(3600);
        // Same hashing SessionService itself uses -- simulates "this token really was issued for this session".
        when(sessionRepository.findByTokenHash(anyHashOf())).thenReturn(Optional.of(stored));
        when(sessionRepository.save(stored)).thenReturn(stored);

        Optional<Session> resolved = sessionService.resolve("some-raw-token");

        assertThat(resolved).isPresent();
        verify(sessionRepository).save(stored); // lastSeen touched
    }

    @Test
    void resolve_withAnUnknownToken_findsNothing() {
        when(sessionRepository.findByTokenHash(anyHashOf())).thenReturn(Optional.empty());

        assertThat(sessionService.resolve("token-nobody-issued")).isEmpty();
    }

    @Test
    void resolve_aRevokedSession_findsNothing() {
        Session revoked = activeSessionExpiringIn(3600);
        revoked.setStatus(SessionStatus.REVOKED);
        when(sessionRepository.findByTokenHash(anyHashOf())).thenReturn(Optional.of(revoked));

        assertThat(sessionService.resolve("some-raw-token")).isEmpty();
    }

    @Test
    void resolve_anExpiredSession_findsNothingAndMarksItExpired() {
        Session expired = activeSessionExpiringIn(-3600); // expired an hour ago
        when(sessionRepository.findByTokenHash(anyHashOf())).thenReturn(Optional.of(expired));
        when(sessionRepository.save(expired)).thenReturn(expired);

        Optional<Session> resolved = sessionService.resolve("some-raw-token");

        assertThat(resolved).isEmpty();
        assertThat(expired.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void revoke_ownSession_succeeds() {
        Session mine = activeSessionExpiringIn(3600);
        when(sessionRepository.findByIdAndUserId(mine.getId(), user.getId())).thenReturn(Optional.of(mine));
        when(sessionRepository.save(mine)).thenReturn(mine);

        sessionService.revoke(user.getId(), mine.getId());

        assertThat(mine.getStatus()).isEqualTo(SessionStatus.REVOKED);
        assertThat(mine.getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_someoneElsesSession_isDenied() {
        // The session id genuinely exists -- it just does not belong to this user.
        when(sessionRepository.findByIdAndUserId("someone-elses-session", user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revoke(user.getId(), "someone-elses-session"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Session activeSessionExpiringIn(long seconds) {
        Session session = new Session();
        session.setUser(user);
        session.setTenantId(user.getTenant().getId());
        session.setTokenHash("irrelevant-in-these-tests");
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plusSeconds(seconds));
        return session;
    }

    /** SessionService hashes internally; these tests don't need to know the exact hash, just match any call. */
    private String anyHashOf() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
