package app.morrow.notification;

import app.morrow.health.HealthSignalSnapshot;
import app.morrow.personalization.PersonalizationService;
import app.morrow.recommendation.Recommendation;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotificationServiceTest {
    private PushDeviceRepository devices;
    private ApnsGateway gateway;
    private PersonalizationService personalization;
    private RecoveryAttemptService attempts;
    private PushNotificationService service;
    private PushDevice device;
    private HealthSignalSnapshot snapshot;

    @BeforeEach
    void setUp() {
        devices = mock(PushDeviceRepository.class);
        gateway = mock(ApnsGateway.class);
        personalization = mock(PersonalizationService.class);
        attempts = mock(RecoveryAttemptService.class);
        var currentUtcHour = OffsetDateTime.now(ZoneOffset.UTC).getHour();
        var daytimeZone = ZoneOffset.ofHours(12 - currentUtcHour).getId();
        service = new PushNotificationService(devices, gateway, mock(ApnsProperties.class), daytimeZone,
                personalization, attempts, 90, 30);
        device = new PushDevice("user-a", "a".repeat(64), PushDevice.Platform.IOS, PushDevice.Environment.PRODUCTION);
        snapshot = new HealthSignalSnapshot("user-a", "snapshot-a", HealthSignalSnapshot.Source.IPHONE,
                300, 90.0, 80.0, 25.0, 1_000.0, 50.0, 5.0, 500.0, 0.0, 18.0, 97.0,
                OffsetDateTime.now());
        when(devices.findActiveForDelivery("user-a")).thenReturn(List.of(device));
        when(personalization.personalizeProactiveAction(anyString(), any()))
                .thenAnswer(invocation -> new PersonalizationService.ProactiveAction(invocation.getArgument(1), false, ""));
        when(attempts.prepareSuggestion(eq("user-a"), any(), anyString(), anyString(), anyString(), eq(RecoveryAttempt.Source.NOTIFICATION)))
                .thenAnswer(invocation -> new RecoveryAttempt("user-a", invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), RecoveryAttempt.Source.NOTIFICATION));
    }

    @Test
    void sendsAiCopyAndPersistsSuggestionOnlyWhenApnsAccepts() {
        when(gateway.send(eq(device), anyString(), anyString(), eq("MORROW_ACTION"), any()))
                .thenReturn(new ApnsGateway.SendResult(true, 200, "Accepted"));
        var title = ArgumentCaptor.forClass(String.class);
        var body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") var data = ArgumentCaptor.forClass(Map.class);

        var result = service.sendAiRecoveryAlert(snapshot, 55, "수면이 짧아요.", "지금 물 한 잔 어때요?", "물을 마시고 3분만 걸어보세요.");

        verify(gateway).send(eq(device), title.capture(), body.capture(), eq("MORROW_ACTION"), data.capture());
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(title.getValue()).isEqualTo("지금 물 한 잔 어때요?");
        assertThat(body.getValue()).isEqualTo("물을 마시고 3분만 걸어보세요.");
        assertThat(data.getValue()).containsEntry("action", "WATER_WALK");
        verify(attempts).recordDeliveredSuggestion(any(RecoveryAttempt.class));
    }

    @Test
    void doesNotPersistSuggestionWhenNoDeviceAcceptsRecommendation() {
        when(gateway.send(eq(device), anyString(), anyString(), eq("MORROW_ACTION"), any()))
                .thenReturn(new ApnsGateway.SendResult(false, 503, "APNsNotConfigured"));
        var recommendation = new Recommendation("user-a", "지금 1분 호흡해요", "긴장을 낮춰보세요.",
                Recommendation.Status.ACTIVE, RecoveryAttempt.Action.BREATH, 60, Recommendation.Source.AI);

        var result = service.sendRecommendationAlert(recommendation);

        assertThat(result.accepted()).isZero();
        verify(attempts, never()).recordDeliveredSuggestion(any());
    }

    @Test
    void recoveryCooldownIsNinetyMinutesInsteadOfSixHours() {
        device.markRecoveryNotified(OffsetDateTime.now().minusMinutes(60));

        var result = service.sendActionableRecoveryAlert(snapshot, 55, "수면이 짧아요.", "HIGH");

        assertThat(result.accepted()).isZero();
        verify(gateway, never()).send(any(), anyString(), anyString(), anyString(), any());
        verify(attempts, never()).recordDeliveredSuggestion(any());
    }

    @Test
    void recoveryCanSendAgainAfterNinetyMinutes() {
        device.markRecoveryNotified(OffsetDateTime.now().minusMinutes(91));
        when(gateway.send(eq(device), anyString(), anyString(), eq("MORROW_ACTION"), any()))
                .thenReturn(new ApnsGateway.SendResult(true, 200, "Accepted"));

        var result = service.sendActionableRecoveryAlert(snapshot, 55, "수면이 짧아요.", "HIGH");

        assertThat(result.accepted()).isEqualTo(1);
    }
}
