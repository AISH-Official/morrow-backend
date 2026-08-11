package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.dashboard.RecoveryScoreCalculator;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.checkin.CheckInRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPushListener {
    private final PushNotificationService notifications;
    private final RecoveryScoreCalculator recoveryScores;
    private final HealthSignalSnapshotRepository healthSnapshots;
    private final CheckInRepository checkIns;
    private final java.time.ZoneId timeZone;
    public HealthPushListener(PushNotificationService notifications, RecoveryScoreCalculator recoveryScores, HealthSignalSnapshotRepository healthSnapshots, CheckInRepository checkIns, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone) {
        this.notifications = notifications; this.recoveryScores = recoveryScores; this.healthSnapshots = healthSnapshots; this.checkIns = checkIns; this.timeZone = java.time.ZoneId.of(timeZone);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        var snapshot = event.snapshot();
        var history = healthSnapshots.findTop12ByUserIdOrderByRecordedAtDesc(snapshot.getUserId()).stream().filter(value -> !value.getId().equals(snapshot.getId())).toList();
        var start = java.time.LocalDate.now(timeZone).atStartOfDay(timeZone).toOffsetDateTime();
        var today = checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(snapshot.getUserId(), start);
        var assessment = recoveryScores.calculate(snapshot, history, today);
        var load = 100 - assessment.score();
        var reason = assessment.reasons().isEmpty() ? "최근 개인 기준에서 부담 신호가 감지됐어요." : assessment.reasons().get(0);
        if (load >= 55) notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
    }

}
