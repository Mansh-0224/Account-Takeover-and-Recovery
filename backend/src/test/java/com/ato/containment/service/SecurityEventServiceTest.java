package com.ato.containment.service;

import com.ato.containment.model.EventType;
import com.ato.containment.model.SecurityEvent;
import com.ato.containment.model.Severity;
import com.ato.containment.repository.SecurityEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityEventServiceTest {

    @Mock
    private SecurityEventRepository repository;

    @Test
    void record_savesAllTheGivenFields() {
        SecurityEventService service = new SecurityEventService(repository);
        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.record("tenant-a", "user-1", EventType.LOGIN_SUCCESS, "1.2.3.4", "device-1",
                Severity.INFO, "Login succeeded.");

        SecurityEvent saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEventType()).isEqualTo(EventType.LOGIN_SUCCESS);
        assertThat(saved.getIp()).isEqualTo("1.2.3.4");
        assertThat(saved.getDevice()).isEqualTo("device-1");
        assertThat(saved.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(saved.getDescription()).isEqualTo("Login succeeded.");
        assertThat(saved.getId()).isNotBlank();
    }

    @Test
    void record_withNoTenantOrUser_stillSavesForVisibility() {
        // A LOGIN_FAILURE for an email that doesn't exist has no tenant/user to attach.
        SecurityEventService service = new SecurityEventService(repository);
        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.record(null, null, EventType.LOGIN_FAILURE, "1.2.3.4", null, Severity.LOW, "No matching account.");

        assertThat(captor.getValue().getTenantId()).isNull();
        assertThat(captor.getValue().getUserId()).isNull();
    }
}
