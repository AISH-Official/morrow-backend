package app.morrow.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import java.time.Duration;
import app.morrow.personalization.PersonalizationService;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptService;

@Service
@Transactional
public class PushNotificationService {
    private final PushDeviceRepository repository;
    private final ApnsGateway gateway;
    private final ApnsProperties properties;
    private final ZoneId timeZone;
    private final PersonalizationService personalization;
    private final RecoveryAttemptService recoveryAttempts;

    public PushNotificationService(PushDeviceRepository repository, ApnsGateway gateway, ApnsProperties properties, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone, PersonalizationService personalization, RecoveryAttemptService recoveryAttempts) { this.repository = repository; this.gateway = gateway; this.properties = properties; this.timeZone = ZoneId.of(timeZone); this.personalization = personalization; this.recoveryAttempts = recoveryAttempts; }

    public PushDevice register(String userId, String token, PushDevice.Platform platform, PushDevice.Environment environment) {
        var normalized = token.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (normalized.length() < 32) throw new IllegalArgumentException("유효하지 않은 APNs 토큰입니다.");
        var device = repository.findByDeviceToken(normalized).orElseGet(() -> new PushDevice(userId, normalized, platform, environment));
        device.refresh(userId, platform, environment); return repository.save(device);
    }

    public void unregister(String userId, String token) { repository.findByDeviceToken(token.toLowerCase()).filter(value -> value.getUserId().equals(userId)).ifPresent(PushDevice::deactivate); }

    public void unregisterAll(String userId) {
        repository.findByUserIdAndActiveTrue(userId).forEach(PushDevice::deactivate);
    }

    public DispatchResult send(String userId, String title, String body, String category, Map<String, Object> data, boolean respectCooldown) {
        return send(userId, title, body, category, data, respectCooldown ? DeliveryKind.RECOVERY : DeliveryKind.GENERAL);
    }

    private DispatchResult send(String userId, String title, String body, String category, Map<String, Object> data, DeliveryKind kind) {
        var results = new ArrayList<DeviceResult>(); var now = OffsetDateTime.now();
        var localHour = now.atZoneSameInstant(timeZone).getHour();
        if (kind != DeliveryKind.GENERAL && (localHour >= 22 || localHour < 8)) return new DispatchResult(0, 0, java.util.List.of());
        for (var device : repository.findByUserIdAndActiveTrue(userId)) {
            if (onCooldown(device, kind, now)) { results.add(new DeviceResult(device.getPlatform(), false, 429, "Cooldown")); continue; }
            var result = gateway.send(device, title, body, category, data);
            if (result.accepted()) markDelivered(device, kind, now);
            if (result.statusCode() == 410 || "BadDeviceToken".equals(result.reason()) || "Unregistered".equals(result.reason())) device.deactivate();
            results.add(new DeviceResult(device.getPlatform(), result.accepted(), result.statusCode(), result.reason()));
        }
        return new DispatchResult(results.size(), results.stream().filter(DeviceResult::accepted).count(), results);
    }

    @Transactional(readOnly = true)
    public boolean canSendRecoveryAlert(String userId) {
        var now = OffsetDateTime.now();
        if (inQuietHours(now)) return false;
        var threshold = now.minusHours(6);
        return repository.findByUserIdAndActiveTrue(userId).stream()
                .anyMatch(device -> device.getLastNotifiedAt() == null || !device.getLastNotifiedAt().isAfter(threshold));
    }

    /**
     * The AI judge regulates its own send frequency, so this gate only avoids
     * pointless OpenAI calls: outside quiet hours and with at least one device.
     */
    @Transactional(readOnly = true)
    public boolean canEvaluateAiRecoveryAlert(String userId) {
        if (inQuietHours(OffsetDateTime.now())) return false;
        return !repository.findByUserIdAndActiveTrue(userId).isEmpty();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<OffsetDateTime> lastRecoveryAlertAt(String userId) {
        return repository.findByUserIdAndActiveTrue(userId).stream()
                .map(PushDevice::getLastNotifiedAt)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder());
    }

    private boolean inQuietHours(OffsetDateTime now) {
        var localHour = now.atZoneSameInstant(timeZone).getHour();
        return localHour >= 22 || localHour < 8;
    }

    public DispatchResult sendHourlyCheckInReminder(String userId) {
        return send(userId,
                "지금 상태를 30초만 확인해요",
                "몸과 마음이 어떤지 체크인하면 다음 추천이 더 정확해져요.",
                "MORROW_CHECKIN",
                Map.of("type", "CHECKIN", "source", "HOURLY_REMINDER"),
                DeliveryKind.CHECK_IN);
    }

    private boolean onCooldown(PushDevice device, DeliveryKind kind, OffsetDateTime now) {
        return switch (kind) {
            case RECOVERY -> within(device.getLastNotifiedAt(), now, Duration.ofHours(6));
            case CHECK_IN -> within(device.getLastCheckInNotifiedAt(), now, Duration.ofMinutes(50));
            // Frequency is decided by the AI judge, which sees the last alert time.
            case AI_RECOVERY, GENERAL -> false;
        };
    }

    private boolean within(OffsetDateTime last, OffsetDateTime now, Duration duration) {
        return last != null && last.isAfter(now.minus(duration));
    }

    private void markDelivered(PushDevice device, DeliveryKind kind, OffsetDateTime now) {
        if (kind == DeliveryKind.RECOVERY || kind == DeliveryKind.AI_RECOVERY) device.markRecoveryNotified(now);
        if (kind == DeliveryKind.CHECK_IN) device.markCheckInNotified(now);
    }

    public DispatchResult sendRecoveryAlert(String userId, int load) {
        return send(userId, "지금 1분만 호흡해요", "손목에서 시작하고 4초 들이마신 뒤 6초 내쉬기를 6번 반복해 보세요.", "MORROW_ACTION", Map.of("type", "RECOVERY", "action", "BREATH", "load", load), true);
    }

    public DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load) {
        return sendActionableRecoveryAlert(snapshot, load, "최근 개인 기준에서 부담 신호가 감지됐어요.", "LOW");
    }

    public DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load, String reason, String confidence) {
        return sendActionableRecoveryAlert(snapshot, load, reason, confidence, DeliveryKind.RECOVERY);
    }

    public DispatchResult sendAiRecoveryAlert(HealthSignalSnapshot snapshot, int load, String reason) {
        return sendActionableRecoveryAlert(snapshot, load, reason, "AI", DeliveryKind.AI_RECOVERY);
    }

    private DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load, String reason, String confidence, DeliveryKind kind) {
        var defaultAction = RecoveryAttempt.Action.BREATH;
        var trigger = "RECOVERY_LOAD";
        if (snapshot.getSleepMinutes() != null && snapshot.getSleepMinutes() > 0 && snapshot.getSleepMinutes() < 360) {
            defaultAction = RecoveryAttempt.Action.WATER_WALK;
            trigger = "SHORT_SLEEP";
        } else if (snapshot.getSteps() != null && snapshot.getSteps() < 2500) {
            defaultAction = RecoveryAttempt.Action.WALK;
            trigger = "LOW_ACTIVITY";
        } else if (snapshot.getRestingHeartRate() != null && snapshot.getRestingHeartRate() > 78) {
            defaultAction = RecoveryAttempt.Action.BREATH;
            trigger = "ELEVATED_RESTING_HEART_RATE";
        } else if (snapshot.getExerciseMinutes() != null && snapshot.getExerciseMinutes() >= 45) {
            defaultAction = RecoveryAttempt.Action.STRETCH;
            trigger = "POST_EXERCISE";
        }
        var selected = personalization.personalizeProactiveAction(snapshot.getUserId(), defaultAction);
        var content = contentFor(selected.action());
        var explanation = reason == null || reason.isBlank() ? "최근 개인 기준에서 부담 신호가 감지됐어요." : reason;
        var body = explanation + " " + content.body() + (selected.personalized() ? " " + selected.rationale() : "");
        var attempt = recoveryAttempts.suggest(snapshot.getUserId(), selected.action(), trigger, explanation, confidence, RecoveryAttempt.Source.NOTIFICATION);
        return send(snapshot.getUserId(), content.title(), body, "MORROW_ACTION", Map.of(
                "type", "RECOVERY", "action", selected.action().name(), "load", load,
                "attemptId", attempt.getId().toString(), "reason", explanation,
                "confidence", confidence, "durationSeconds", content.durationSeconds()), kind);
    }

    private ActionContent contentFor(RecoveryAttempt.Action action) {
        return switch (action) {
            case BREATH -> new ActionContent("지금 1분만 호흡해요", "4초 들이마시고 6초 내쉬기를 반복해 보세요.", 60);
            case WALK -> new ActionContent("지금 5분 걸어볼까요?", "자리에서 일어나 가까운 곳까지 가볍게 걸어보세요.", 300);
            case WATER_WALK -> new ActionContent("물 한 잔 뒤 가볍게 걸어요", "물을 마시고 3분만 천천히 걸어 몸을 깨워보세요.", 180);
            case STRETCH -> new ActionContent("지금 3분 스트레칭해요", "어깨와 목부터 천천히 풀고 길게 숨을 내쉬어 보세요.", 180);
            case FOCUS -> new ActionContent("할 일 하나만 5분 시작해요", "방해 요소를 닫고 가장 작은 한 단계부터 시작해 보세요.", 300);
            case SCREEN_BREAK -> new ActionContent("지금 1분 화면에서 눈을 떼요", "먼 곳을 바라보고 어깨 힘을 천천히 빼보세요.", 60);
        };
    }

    @Transactional(readOnly = true) public Status status() { return new Status(properties.isEnabled(), properties.ready(), repository.countByActiveTrue(), properties.getIosTopic(), properties.getWatchTopic()); }
    public record DispatchResult(int attempted, long accepted, java.util.List<DeviceResult> devices) {}
    public record DeviceResult(PushDevice.Platform platform, boolean accepted, int statusCode, String reason) {}
    public record Status(boolean enabled, boolean ready, long activeDevices, String iosTopic, String watchTopic) {}
    private record ActionContent(String title, String body, int durationSeconds) {}
    private enum DeliveryKind { GENERAL, RECOVERY, AI_RECOVERY, CHECK_IN }
}
