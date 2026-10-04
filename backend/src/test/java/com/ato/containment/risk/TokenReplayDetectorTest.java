package com.ato.containment.risk;

import com.ato.containment.model.Role;
import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenReplayDetectorTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;
    @Mock
    private UserRepository userRepository;

    private TokenReplayDetector detector;
    private User user;

    @BeforeEach
    void setUp() {
        RiskEngine riskEngine = new RiskEngine(sessionRepository);
        detector = new TokenReplayDetector(sessionRepository, securityEventService, riskEngine, riskAssessmentRepository, userRepository);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
    }

    private Session sessionSeenRecently(String deviceId, String ip, int secondsAgo) {
        Session session = new Session();
        session.setUser(user);
        session.setTenantId(user.getTenant().getId());
        session.setDeviceId(deviceId);
        session.setIpAddress(ip);
        session.setStatus(SessionStatus.ACTIVE);
        session.setLastSeen(Instant.now().minusSeconds(secondsAgo)); // the detector compares against this first
        return session;
    }

    @Test
    void sameDeviceAndIp_repeatedUse_isNeverFlagged() {
        Session session = sessionSeenRecently("device-1", "1.2.3.4", 30);

        boolean replay = detector.checkAndHandle(session, "device-1", "1.2.3.4");

        assertThat(replay).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE); // untouched
    }

    @Test
    void unknownDeviceInfo_isNeverFlagged() {
        // A session that never recorded a device (e.g. created via a plain
        // curl call with no X-Device-Id) can't be compared -- must not false-positive.
        Session session = sessionSeenRecently(null, null, 30);

        boolean replay = detector.checkAndHandle(session, "some-device", "9.9.9.9");

        assertThat(replay).isFalse();
    }

    @Test
    void differentDevice_withinWindow_isFlaggedAndSessionRevoked() {
        Session session = sessionSeenRecently("device-1", "1.2.3.4", 30); // seen 30s ago, well within the 10-min window
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());

        boolean replay = detector.checkAndHandle(session, "device-2", "1.2.3.4");

        assertThat(replay).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.REVOKED);
        assertThat(session.getRevokedAt()).isNotNull();
    }

    @Test
    void differentDevice_withinWindow_containsTheAccount() {
        Session session = sessionSeenRecently("device-1", "1.2.3.4", 30);
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());

        detector.checkAndHandle(session, "device-2", "1.2.3.4");

        assertThat(user.getStatus()).isEqualTo(UserStatus.CONTAINED);
    }

    @Test
    void differentDevice_outsideWindow_isNotFlagged() {
        // Last seen a long time ago (well outside the 10-minute replay window) --
        // a genuine device switch over time, not a sudden suspicious reuse.
        Session session = sessionSeenRecently("device-1", "1.2.3.4", 3600); // an hour ago

        boolean replay = detector.checkAndHandle(session, "device-2", "9.9.9.9");

        assertThat(replay).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void differentIpOnly_sameDevice_withinWindow_isStillFlagged() {
        Session session = sessionSeenRecently("device-1", "1.2.3.4", 30);
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());

        boolean replay = detector.checkAndHandle(session, "device-1", "9.9.9.9");

        assertThat(replay).isTrue();
    }
}
