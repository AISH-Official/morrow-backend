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
    private final int aiEvaluationMinLoad;
    public HealthPushListener(PushNotificationService notifications, RecoveryScoreCalculator recoveryScores, HealthSignalSnapshotRepository healthSnapshots, CheckInRepository checkIns, AssistantService assistant, AccountAuthService accounts, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone, @Value("${morrow.push.ai-evaluation-min-load:20}") int aiEvaluationMinLoad) {
        this.notifications = notifications; this.recoveryScores = recoveryScores; this.healthSnapshots = healthSnapshots; this.checkIns = checkIns; this.assistant = assistant; this.accounts = accounts; this.timeZone = java.time.ZoneId.of(timeZone);
        this.aiEvaluationMinLoad = aiEvaluationMinLoad;
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
        // The rule-based path keeps its own >= 55 threshold below, so this gate
        // only decides how early the AI judge gets a chance to evaluate.
        if (load < aiEvaluationMinLoad) return;
        var userId = snapshot.getUserId();

        // AI alerts carry no fixed cooldown: the judge sees when the user was
        // last notified and regulates its own send frequency.
        if (accounts.aiHealthConsent(userId) && notifications.canEvaluateAiRecoveryAlert(userId)) {
            var lastAlertAt = notifications.lastRecoveryAlertAt(userId).orElse(null);
            var insight = assistant.generateProactiveInsight(userId, lastAlertAt);
            if (insight.mode() == OpenAIClient.Mode.LIVE) {
                if (insight.shouldNotify()) {
                    log.info("Sending AI recovery alert. userId={}, load={}", userId, load);
                    notifications.sendAiRecoveryAlert(snapshot, load, reason);
                    return;
                }
                // AI skip is honored in the mid band, but a high load means clear
                // strain signals, so fall through to the rule-based alert instead
                // of dropping the notification entirely.
                if (load < RULE_ALERT_LOAD_THRESHOLD) {
                    log.info("Recovery alert suppressed by AI skip. userId={}, load={}, reason={}", userId, load, insight.reason());
                    return;
                }
                log.info("AI skipped at high load; using rule-based alert. userId={}, load={}", userId, load);
            } else {
                log.info("AI unavailable for recovery alert; using rule-based path. userId={}, load={}, reason={}", userId, load, insight.reason());
            }
        }

        // The non-AI path keeps the 6-hour cooldown because nothing else limits it.
        if (load >= RULE_ALERT_LOAD_THRESHOLD && notifications.canSendRecoveryAlert(userId)) {
            notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
        }
    }

}
