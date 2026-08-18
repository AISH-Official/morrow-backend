package app.morrow.notification;

import app.morrow.assistant.AssistantService;
import app.morrow.assistant.OpenAIClient;
import app.morrow.auth.AccountAuthService;
import app.morrow.checkin.CheckInRepository;
import app.morrow.dashboard.RecoveryScoreCalculator;
import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.health.HealthSignalSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthPushListenerTest {
    private PushNotificationService notifications;
    private RecoveryScoreCalculator recoveryScores;
    private HealthSignalSnapshotRepository healthSnapshots;
    private CheckInRepository checkIns;
    private AssistantService assistant;
    private AccountAuthService accounts;
    private HealthPushListener listener;
    private HealthSignalSnapshot snapshot;

    @BeforeEach
    void setUp() {
        notifications = mock(PushNotificationService.class);
        recoveryScores = mock(RecoveryScoreCalculator.class);
        healthSnapshots = mock(HealthSignalSnapshotRepository.class);
        checkIns = mock(CheckInRepository.class);
        assistant = mock(AssistantService.class);
        accounts = mock(AccountAuthService.class);
        listener = new HealthPushListener(notifications, recoveryScores, healthSnapshots, checkIns, assistant, accounts, "Asia/Seoul", 20);
        snapshot = new HealthSignalSnapshot("user-a", "snapshot-a", HealthSignalSnapshot.Source.WATCH, 300, 92.0, 81.0, 28.0, 1200.0, 80.0, 5.0, 500.0, 1.0, 18.0, 97.0, OffsetDateTime.now());
        when(healthSnapshots.findTop12ByUserIdOrderByRecordedAtDesc("user-a")).thenReturn(List.of(snapshot));
        when(checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(anyString(), any())).thenReturn(List.of());
        when(notifications.canSendRecoveryAlert("user-a")).thenReturn(true);
        when(notifications.canEvaluateAiRecoveryAlert("user-a")).thenReturn(true);
        when(notifications.lastRecoveryAlertAt("user-a")).thenReturn(Optional.empty());
    }

    @Test
    void sendsWhenConsentedAiDecidesNotificationIsUseful() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(55, "MODERATE", true, "HIGH", List.of("수면이 최근 기준보다 짧아요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(assistant.generateProactiveInsight(eq("user-a"), any())).thenReturn(new AssistantService.ProactiveInsight(true, "지금 1분 호흡해요", "짧게 호흡해 보세요.", OpenAIClient.Mode.LIVE, "RECENT_WELLNESS_CONTEXT"));

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(notifications).sendAiRecoveryAlert(snapshot, 45, "수면이 최근 기준보다 짧아요.");
    }

    @Test
    void evaluatesWithAiAtLightLoadAndSendsWhenAiDecidesSo() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(75, "NORMAL", true, "MEDIUM", List.of("걸음이 같은 시간대보다 적어요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(assistant.generateProactiveInsight(eq("user-a"), any())).thenReturn(new AssistantService.ProactiveInsight(true, "지금 잠깐 걸어볼까요?", "잠깐 걸어보세요.", OpenAIClient.Mode.LIVE, "RECENT_WELLNESS_CONTEXT"));

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(notifications).sendAiRecoveryAlert(snapshot, 25, "걸음이 같은 시간대보다 적어요.");
    }

    @Test
    void aiAlertIgnoresRuleCooldownAndReceivesLastAlertTime() {
        var lastAlertAt = OffsetDateTime.now().minusMinutes(90);
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(55, "MODERATE", true, "HIGH", List.of("수면이 최근 기준보다 짧아요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(notifications.canSendRecoveryAlert("user-a")).thenReturn(false);
        when(notifications.lastRecoveryAlertAt("user-a")).thenReturn(Optional.of(lastAlertAt));
        when(assistant.generateProactiveInsight("user-a", lastAlertAt)).thenReturn(new AssistantService.ProactiveInsight(true, "지금 1분 호흡해요", "짧게 호흡해 보세요.", OpenAIClient.Mode.LIVE, "RECENT_WELLNESS_CONTEXT"));

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(notifications).sendAiRecoveryAlert(snapshot, 45, "수면이 최근 기준보다 짧아요.");
    }

    @Test
    void skipsEvaluationBelowConfiguredMinimumLoad() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(90, "NORMAL", true, "HIGH", List.of()));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(assistant, never()).generateProactiveInsight(anyString(), any());
        verify(notifications, never()).sendAiRecoveryAlert(any(), anyInt(), anyString());
        verify(notifications, never()).sendActionableRecoveryAlert(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void respectsAiSkipDecisionInMidLoadBand() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(60, "MODERATE", true, "HIGH", List.of("HRV가 최근 기준보다 낮아요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(assistant.generateProactiveInsight(eq("user-a"), any())).thenReturn(new AssistantService.ProactiveInsight(false, "", "", OpenAIClient.Mode.LIVE, "AI_SKIPPED"));

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(notifications, never()).sendAiRecoveryAlert(any(), anyInt(), anyString());
        verify(notifications, never()).sendActionableRecoveryAlert(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void fallsBackToRuleAlertWhenAiSkipsAtHighLoad() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(45, "MODERATE", true, "HIGH", List.of("HRV가 최근 기준보다 낮아요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(assistant.generateProactiveInsight(eq("user-a"), any())).thenReturn(new AssistantService.ProactiveInsight(false, "", "", OpenAIClient.Mode.LIVE, "AI_SKIPPED"));

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(notifications).sendActionableRecoveryAlert(snapshot, 55, "HRV가 최근 기준보다 낮아요.", "HIGH");
    }

    @Test
    void usesSafeRuleFallbackWithoutAiHealthConsent() {
        when(recoveryScores.calculate(any(), any(), any())).thenReturn(new RecoveryScoreCalculator.Assessment(40, "HIGHER_THAN_USUAL", true, "MEDIUM", List.of("안정 심박이 최근 기준보다 높아요.")));
        when(accounts.aiHealthConsent("user-a")).thenReturn(false);

        listener.onSnapshot(new HealthSignalSnapshotService.HealthSnapshotCreatedEvent(snapshot));

        verify(assistant, never()).generateProactiveInsight(anyString(), any());
        verify(notifications).sendActionableRecoveryAlert(snapshot, 60, "안정 심박이 최근 기준보다 높아요.", "MEDIUM");
    }
}
