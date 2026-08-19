package app.morrow.notification;

import app.morrow.assistant.AssistantService;
import app.morrow.assistant.OpenAIClient;
import app.morrow.auth.AccountAuthService;
import app.morrow.checkin.CheckInRepository;
import app.morrow.dashboard.RecoveryScoreCalculator;
import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.health.HealthSignalSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class HealthPushListener {
    private static final Logger log = LoggerFactory.getLogger(HealthPushListener.class);

    private final PushNotificationService notifications;
    private final RecoveryScoreCalculator recoveryScores;
    private final HealthSignalSnapshotRepository healthSnapshots;
    private final CheckInRepository checkIns;
    private final AssistantService assistant;
    private final AccountAuthService accounts;
    private final ZoneId timeZone;
    private final int aiEvaluationMinLoad;
    private final int highLoadFallbackThreshold;
    private final Duration maxSnapshotAge;

    public HealthPushListener(
            PushNotificationService notifications,
            RecoveryScoreCalculator recoveryScores,
            HealthSignalSnapshotRepository healthSnapshots,
            CheckInRepository checkIns,
            AssistantService assistant,
            AccountAuthService accounts,
            @Value("${morrow.time-zone:Asia/Seoul}") String timeZone,
            @Value("${morrow.push.ai-evaluation-min-load:20}") int aiEvaluationMinLoad,
            @Value("${morrow.push.high-load-fallback-threshold:50}") int highLoadFallbackThreshold,
            @Value("${morrow.push.max-health-snapshot-age-hours:24}") long maxSnapshotAgeHours
    ) {
        this.notifications = notifications;
        this.recoveryScores = recoveryScores;
        this.healthSnapshots = healthSnapshots;
        this.checkIns = checkIns;
        this.assistant = assistant;
        this.accounts = accounts;
        this.timeZone = ZoneId.of(timeZone);
        this.aiEvaluationMinLoad = Math.max(0, aiEvaluationMinLoad);
        this.highLoadFallbackThreshold = Math.max(35, highLoadFallbackThreshold);
        this.maxSnapshotAge = Duration.ofHours(Math.max(1, maxSnapshotAgeHours));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnapshot(HealthSignalSnapshotService.HealthSnapshotCreatedEvent event) {
        evaluate(event.snapshot());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateLatest(String userId) {
        healthSnapshots.findFirstByUserIdOrderByRecordedAtDesc(userId)
                .filter(snapshot -> snapshot.getRecordedAt() != null)
                .filter(snapshot -> snapshot.getRecordedAt().isAfter(OffsetDateTime.now().minus(maxSnapshotAge)))
                .ifPresent(this::evaluate);
    }

    void evaluate(HealthSignalSnapshot snapshot) {
        var history = healthSnapshots.findTop12ByUserIdOrderByRecordedAtDesc(snapshot.getUserId()).stream()
                .filter(value -> !value.getId().equals(snapshot.getId()))
                .toList();
        var start = LocalDate.now(timeZone).atStartOfDay(timeZone).toOffsetDateTime();
        var today = checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(snapshot.getUserId(), start);
        var assessment = recoveryScores.calculate(snapshot, history, today);
        var load = 100 - assessment.score();
        var reason = assessment.reasons().isEmpty()
                ? "최근 개인 기준에서 부담 신호가 감지됐어요."
                : assessment.reasons().get(0);
        if (load < aiEvaluationMinLoad) return;

        var userId = snapshot.getUserId();
        if (accounts.aiHealthConsent(userId) && notifications.canEvaluateAiRecoveryAlert(userId)) {
            var lastAlertAt = notifications.lastRecoveryAlertAt(userId).orElse(null);
            var insight = assistant.generateProactiveInsight(userId, lastAlertAt);
            if (insight.mode() == OpenAIClient.Mode.LIVE) {
                if (insight.shouldNotify()) {
                    log.info("Sending AI recovery alert: load={}", load);
                    notifications.sendAiRecoveryAlert(snapshot, load, reason, insight.title(), insight.body());
                    return;
                }
                if (load < highLoadFallbackThreshold) {
                    log.info("Recovery alert suppressed by AI: load={}, reason={}", load, insight.reason());
                    return;
                }
                log.info("AI skipped at high load; using safe fallback: load={}", load);
            } else {
                log.info("AI unavailable; using safe recovery path: load={}, reason={}", load, insight.reason());
            }
        }

        if (load >= highLoadFallbackThreshold && notifications.canSendRecoveryAlert(userId)) {
            notifications.sendActionableRecoveryAlert(snapshot, load, reason, assessment.confidence());
        }
    }
}
