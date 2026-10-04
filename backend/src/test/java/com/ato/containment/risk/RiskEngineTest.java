package com.ato.containment.risk;

import com.ato.containment.model.RiskLevel;
import com.ato.containment.model.RiskSignal;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock
    private SessionRepository sessionRepository;

    private RiskEngine riskEngine;
    private User user;

    @BeforeEach
    void setUp() {
        riskEngine = new RiskEngine(sessionRepository);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setFailedLoginCount(0);
        user.setPasswordChangedAt(Instant.now().minusSeconds(3600)); // an hour ago, not "recent"
    }

    @Test
    void cleanLogin_knownDeviceAndLocation_noFailedAttempts_scoresLow() {
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "known-device")).thenReturn(true);
        when(sessionRepository.existsByUserIdAndLocation(user.getId(), "London, UK")).thenReturn(true);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        RiskEngine.Result result = riskEngine.assessLogin(user, "known-device", "1.2.3.4", "London, UK");

        assertThat(result.score()).isZero();
        assertThat(result.level()).isEqualTo(RiskLevel.LOW);
        assertThat(result.signals()).isEmpty();
    }

    @Test
    void newDeviceAndUnusualLocation_scoresMedium() {
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "brand-new-device")).thenReturn(false);
        when(sessionRepository.existsByUserIdAndLocation(user.getId(), "Lagos, NG")).thenReturn(false);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        RiskEngine.Result result = riskEngine.assessLogin(user, "brand-new-device", "9.9.9.9", "Lagos, NG");

        assertThat(result.score()).isEqualTo(40); // NEW_DEVICE(20) + UNUSUAL_LOCATION(20)
        assertThat(result.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.signals()).containsExactlyInAnyOrder(RiskSignal.NEW_DEVICE, RiskSignal.UNUSUAL_LOCATION);
    }

    @Test
    void manySignalsAtOnce_scoresHigh() {
        user.setFailedLoginCount(5); // >= threshold
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "brand-new-device")).thenReturn(false);
        when(sessionRepository.existsByUserIdAndLocation(user.getId(), "Lagos, NG")).thenReturn(false);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        RiskEngine.Result result = riskEngine.assessLogin(user, "brand-new-device", "9.9.9.9", "Lagos, NG");

        // NEW_DEVICE(20) + UNUSUAL_LOCATION(20) + MULTIPLE_FAILED_LOGINS(20) = 60
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void recentPasswordChange_addsSignal() {
        user.setPasswordChangedAt(Instant.now().minusSeconds(60)); // 1 minute ago -- within the window
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "known-device")).thenReturn(true);
        // location is null below, so existsByUserIdAndLocation is never called -- no stub needed for it.
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        RiskEngine.Result result = riskEngine.assessLogin(user, "known-device", "1.2.3.4", null);

        assertThat(result.signals()).contains(RiskSignal.PASSWORD_CHANGED);
        assertThat(result.score()).isEqualTo(30);
    }

    @Test
    void thresholdBoundaries_classifyCorrectly() {
        assertThat(RiskEngine.classify(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskEngine.classify(29)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskEngine.classify(30)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskEngine.classify(59)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskEngine.classify(60)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskEngine.classify(100)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void sameConditionEvaluatedOnce_signalNeverDuplicated() {
        // Even though NEW_DEVICE's condition could theoretically be checked
        // more than once in a naive implementation, the engine uses a Set and
        // evaluates each rule exactly once -- this asserts the outcome stays
        // a single instance of the signal, never appearing twice.
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "brand-new-device")).thenReturn(false);
        // location is null below, so existsByUserIdAndLocation is never called -- no stub needed for it.
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        RiskEngine.Result result = riskEngine.assessLogin(user, "brand-new-device", "1.2.3.4", null);

        long newDeviceCount = result.signals().stream().filter(s -> s == RiskSignal.NEW_DEVICE).count();
        assertThat(newDeviceCount).isEqualTo(1);
    }

    @Test
    void tokenReplaySignal_scoresFiftyAndIsMedium() {
        RiskEngine.Result result = riskEngine.assessTokenReplay();

        assertThat(result.score()).isEqualTo(50);
        assertThat(result.level()).isEqualTo(RiskLevel.MEDIUM); // per the given thresholds: 50 falls in 30-59
        assertThat(result.signals()).containsExactly(RiskSignal.TOKEN_REPLAY);
    }
}
