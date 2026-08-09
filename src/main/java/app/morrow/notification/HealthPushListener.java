package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPushListener {
    private final PushNotificationService notifications;
    public HealthPushListener(PushNotificationService notifications) { this.notifications = notifications; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        var snapshot = event.snapshot();
        var load = recoveryLoad(snapshot);
        if (load >= 70) notifications.sendRecoveryAlert(snapshot.getUserId(), load);
    }

    static int recoveryLoad(HealthSignalSnapshot value) {
        var load = 30;
        if (positive(value.getSleepMinutes()) && value.getSleepMinutes() < 420) load += Math.min(30, (420 - value.getSleepMinutes()) / 4);
        if (positive(value.getHrv()) && value.getHrv() < 45) load += Math.min(25, (int)Math.round(45 - value.getHrv()));
        if (positive(value.getRestingHeartRate()) && value.getRestingHeartRate() > 72) load += Math.min(20, (int)Math.round(value.getRestingHeartRate() - 72));
        if (positive(value.getExerciseMinutes()) && value.getExerciseMinutes() >= 20) load -= 8;
        return Math.max(0, Math.min(100, load));
    }

    private static boolean positive(Number value) { return value != null && value.doubleValue() > 0; }
}
