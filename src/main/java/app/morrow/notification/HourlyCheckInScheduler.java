package app.morrow.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "morrow.push.check-in-reminders-enabled", havingValue = "true", matchIfMissing = true)
public class HourlyCheckInScheduler {
    private final PushDeviceRepository devices;
    private final PushNotificationService notifications;

    public HourlyCheckInScheduler(PushDeviceRepository devices, PushNotificationService notifications) {
        this.devices = devices;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${morrow.push.check-in-cron:0 0 * * * *}", zone = "${morrow.time-zone:Asia/Seoul}")
    public void remindActiveUsers() {
        devices.findDistinctActiveUserIds().forEach(notifications::sendHourlyCheckInReminder);
    }
}
