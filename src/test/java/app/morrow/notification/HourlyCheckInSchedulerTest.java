package app.morrow.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HourlyCheckInSchedulerTest {
    @Test
    void sendsOneReminderPerActiveUser() {
        var devices = mock(PushDeviceRepository.class);
        var notifications = mock(PushNotificationService.class);
        when(devices.findDistinctActiveUserIds()).thenReturn(List.of("user-a", "user-b"));

        new HourlyCheckInScheduler(devices, notifications).remindActiveUsers();

        verify(notifications).sendHourlyCheckInReminder("user-a");
        verify(notifications).sendHourlyCheckInReminder("user-b");
    }
}
