package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.dashboard.RecoveryScoreCalculator;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.checkin.CheckInRepository;
import app.morrow.assistant.AssistantService;
import app.morrow.assistant.OpenAIClient;
import app.morrow.auth.AccountAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPushListener {
    private static final Logger log = LoggerFactory.getLogger(HealthPushListener.class);
    private static final int RULE_ALERT_LOAD_THRESHOLD = 55;

    private final PushNotificationService notifications;
    private final RecoveryScoreCalculator recoveryScores;
    private final HealthSignalSnapshotRepository healthSnapshots;
    private final CheckInRepository checkIns;
    private final AssistantService assistant;
    private final AccountAuthService accounts;
    private final java.time.ZoneId timeZone;
    public HealthPushListener(PushNotificationService notifications, RecoveryScoreCalculator recoveryScores, HealthSignalSnapshotRepository healthSnapshots, CheckInRepository checkIns, AssistantService assistant, AccountAuthService accounts, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone) {
        this.notifications = notifications; this.recoveryScores = recoveryScores; this.healthSnapshots = healthSnapshots; this.checkIns = checkIns; this.assistant = assistant; this.accounts = accounts; this.timeZone = java.time.ZoneId.of(timeZone);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        var snapshot = event.snapshot();
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
                    log.info("Sending AI recovery alert. userId={}, load={}", snapshot.getUserId(), load);
                    notifications.sendActionableRecoveryAlert(snapshot, load, reason, "AI");
                    return;
                }
                // AI skip is honored in the mid band, but a high load means clear
                // strain signals, so fall through to the rule-based alert instead
                // of dropping the notification entirely.
                if (load < RULE_ALERT_LOAD_THRESHOLD) {
                    log.info("Recovery alert suppressed by AI skip. userId={}, load={}, reason={}", snapshot.getUserId(), load, insight.reason());
                    return;
                }
                log.info("AI skipped at high load; using rule-based alert. userId={}, load={}", snapshot.getUserId(), load);
            } else {
                log.info("AI unavailable for recovery alert; using rule-based path. userId={}, load={}, reason={}", snapshot.getUserId(), load, insight.reason());
            }
        }

        if (load >= RULE_ALERT_LOAD_THRESHOLD) notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
    }

}
