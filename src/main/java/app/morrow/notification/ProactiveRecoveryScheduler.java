package app.morrow.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "morrow.push.recovery-evaluation-enabled", havingValue = "true", matchIfMissing = true)
public class ProactiveRecoveryScheduler {
    private final PushDeviceRepository devices;
    private final HealthPushListener listener;

    public ProactiveRecoveryScheduler(PushDeviceRepository devices, HealthPushListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @Scheduled(cron = "${morrow.push.recovery-evaluation-cron:0 */30 8-21 * * *}", zone = "${morrow.time-zone:Asia/Seoul}")
    public void evaluateActiveUsers() {
        devices.findDistinctActiveUserIds().forEach(listener::evaluateLatest);
    }
}
