package app.morrow.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProactiveRecoverySchedulerTest {
    @Test
    void reevaluatesEveryActivePushUser() {
        var devices = mock(PushDeviceRepository.class);
        var listener = mock(HealthPushListener.class);
        when(devices.findDistinctActiveUserIds()).thenReturn(List.of("user-a", "user-b"));

        new ProactiveRecoveryScheduler(devices, listener).evaluateActiveUsers();

        verify(listener).evaluateLatest("user-a");
        verify(listener).evaluateLatest("user-b");
    }
}
