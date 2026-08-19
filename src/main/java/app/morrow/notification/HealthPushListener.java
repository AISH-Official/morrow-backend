package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.dashboard.RecoveryScoreCalculator;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.checkin.CheckInRepository;
import app.morrow.assistant.AssistantService;
import app.morrow.assistant.OpenAIClient;
import app.morrow.auth.AccountAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPushListener {
    private final PushNotificationService notifications;
    private final RecoveryScoreCalculator recoveryScores;
    private final HealthSignalSnapshotRepository healthSnapshots;
    private final CheckInRepository checkIns;
    private final AssistantService assistant;
    private final AccountAuthService accounts;
    private final java.time.ZoneId timeZone;
    private final int highLoadFallbackThreshold;
    private final java.time.Duration maxSnapshotAge;
    public HealthPushListener(PushNotificationService notifications, RecoveryScoreCalculator recoveryScores, HealthSignalSnapshotRepository healthSnapshots, CheckInRepository checkIns, AssistantService assistant, AccountAuthService accounts, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone, @Value("${morrow.push.high-load-fallback-threshold:50}") int highLoadFallbackThreshold, @Value("${morrow.push.max-health-snapshot-age-hours:24}") long maxSnapshotAgeHours) {
        this.notifications = notifications; this.recoveryScores = recoveryScores; this.healthSnapshots = healthSnapshots; this.checkIns = checkIns; this.assistant = assistant; this.accounts = accounts; this.timeZone = java.time.ZoneId.of(timeZone);
        this.highLoadFallbackThreshold = Math.max(35, highLoadFallbackThreshold);
        this.maxSnapshotAge = java.time.Duration.ofHours(Math.max(1, maxSnapshotAgeHours));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        evaluate(event.snapshot());
    }

    @Async
    public void evaluateLatest(String userId) {
        healthSnapshots.findFirstByUserIdOrderByRecordedAtDesc(userId)
                .filter(snapshot -> snapshot.getRecordedAt() != null)
                .filter(snapshot -> snapshot.getRecordedAt().isAfter(java.time.OffsetDateTime.now().minus(maxSnapshotAge)))
                .ifPresent(this::evaluate);
    }

    void evaluate(app.morrow.health.HealthSignalSnapshot snapshot) {
        var history = healthSnapshots.findTop12ByUserIdOrderByRecordedAtDesc(snapshot.getUserId()).stream().filter(value -> !value.getId().equals(snapshot.getId())).toList();
        var start = java.time.LocalDate.now(timeZone).atStartOfDay(timeZone).toOffsetDateTime();
        var today = checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(snapshot.getUserId(), start);
        var assessment = recoveryScores.calculate(snapshot, history, today);
        var load = 100 - assessment.score();
        var reason = assessment.reasons().isEmpty() ? "최근 개인 기준에서 부담 신호가 감지됐어요." : assessment.reasons().get(0);
        if (load < 35 || !notifications.canSendRecoveryAlert(snapshot.getUserId())) return;

        if (accounts.aiHealthConsent(snapshot.getUserId())) {
            var insight = assistant.generateProactiveInsight(snapshot.getUserId());
            if (insight.mode() == OpenAIClient.Mode.LIVE) {
                if (insight.shouldNotify()) {
                    notifications.sendAiRecoveryAlert(snapshot, load, reason, insight.title(), insight.body());
                } else if (load >= highLoadFallbackThreshold) {
                    notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
                }
                return;
            }
        }

        if (load >= highLoadFallbackThreshold) notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
    }

}
