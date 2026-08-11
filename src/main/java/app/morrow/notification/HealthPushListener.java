package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.dashboard.RecoveryScoreCalculator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPushListener {
    private final PushNotificationService notifications;
    private final RecoveryScoreCalculator recoveryScores;
    public HealthPushListener(PushNotificationService notifications, RecoveryScoreCalculator recoveryScores) { this.notifications = notifications; this.recoveryScores = recoveryScores; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        var snapshot = event.snapshot();
        var load = 100 - recoveryScores.healthScore(snapshot);
        if (load >= 70) notifications.sendRecoveryAlert(snapshot.getUserId(), load);
    }

}
