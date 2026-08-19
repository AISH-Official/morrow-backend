package app.morrow.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import java.time.Duration;
import app.morrow.personalization.PersonalizationService;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptService;
import app.morrow.recommendation.Recommendation;

@Service
@Transactional
public class PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private final PushDeviceRepository repository;
    private final ApnsGateway gateway;
    private final ApnsProperties properties;
    private final ZoneId timeZone;
    private final PersonalizationService personalization;
    private final RecoveryAttemptService recoveryAttempts;
    private final Duration recoveryCooldown;
    private final Duration recommendationCooldown;

    public PushNotificationService(
            PushDeviceRepository repository,
            ApnsGateway gateway,
            ApnsProperties properties,
            @Value("${morrow.time-zone:Asia/Seoul}") String timeZone,
            PersonalizationService personalization,
            RecoveryAttemptService recoveryAttempts,
            @Value("${morrow.push.recovery-cooldown-minutes:90}") long recoveryCooldownMinutes,
            @Value("${morrow.push.recommendation-cooldown-minutes:30}") long recommendationCooldownMinutes
    ) {
        this.repository = repository;
        this.gateway = gateway;
        this.properties = properties;
        this.timeZone = ZoneId.of(timeZone);
        this.personalization = personalization;
        this.recoveryAttempts = recoveryAttempts;
        this.recoveryCooldown = Duration.ofMinutes(Math.max(15, recoveryCooldownMinutes));
        this.recommendationCooldown = Duration.ofMinutes(Math.max(10, recommendationCooldownMinutes));
    }

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
        if (kind != DeliveryKind.GENERAL && (localHour >= 22 || localHour < 8)) {
            log.info("Push skipped during quiet hours: kind={}", kind);
            return new DispatchResult(0, 0, java.util.List.of());
        }
        for (var device : repository.findActiveForDelivery(userId)) {
            if (onCooldown(device, kind, now)) { results.add(new DeviceResult(device.getPlatform(), false, 429, "Cooldown")); continue; }
            var result = gateway.send(device, title, body, category, data);
            if (result.accepted()) markDelivered(device, kind, now);
            if (result.statusCode() == 410 || "BadDeviceToken".equals(result.reason()) || "Unregistered".equals(result.reason())) device.deactivate();
            results.add(new DeviceResult(device.getPlatform(), result.accepted(), result.statusCode(), result.reason()));
        }
        var dispatch = new DispatchResult(results.size(), results.stream().filter(DeviceResult::accepted).count(), results);
        log.info("Push dispatch completed: kind={}, attempted={}, accepted={}", kind, dispatch.attempted(), dispatch.accepted());
        return dispatch;
    }

    @Transactional(readOnly = true)
    public boolean canSendRecoveryAlert(String userId) {
        var now = OffsetDateTime.now();
        var localHour = now.atZoneSameInstant(timeZone).getHour();
        if (localHour >= 22 || localHour < 8) return false;
        var threshold = now.minus(recoveryCooldown);
        return repository.findByUserIdAndActiveTrue(userId).stream()
                .anyMatch(device -> device.getLastNotifiedAt() == null || !device.getLastNotifiedAt().isAfter(threshold));
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
            case RECOVERY -> within(device.getLastNotifiedAt(), now, recoveryCooldown);
            case RECOMMENDATION -> within(device.getLastNotifiedAt(), now, recommendationCooldown);
            case CHECK_IN -> within(device.getLastCheckInNotifiedAt(), now, Duration.ofMinutes(50));
            case GENERAL -> false;
        };
    }

    private boolean within(OffsetDateTime last, OffsetDateTime now, Duration duration) {
        return last != null && last.isAfter(now.minus(duration));
    }

    private void markDelivered(PushDevice device, DeliveryKind kind, OffsetDateTime now) {
        if (kind == DeliveryKind.RECOVERY || kind == DeliveryKind.RECOMMENDATION) device.markRecoveryNotified(now);
        if (kind == DeliveryKind.CHECK_IN) device.markCheckInNotified(now);
    }

    public DispatchResult sendRecoveryAlert(String userId, int load) {
        return send(userId, "지금 1분만 호흡해요", "손목에서 시작하고 4초 들이마신 뒤 6초 내쉬기를 6번 반복해 보세요.", "MORROW_ACTION", Map.of("type", "RECOVERY", "action", "BREATH", "load", load), true);
    }

    public DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load) {
        return sendActionableRecoveryAlert(snapshot, load, "최근 개인 기준에서 부담 신호가 감지됐어요.", "LOW");
    }

    public DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load, String reason, String confidence) {
        var defaultSelection = defaultActionFor(snapshot);
        var selected = personalization.personalizeProactiveAction(snapshot.getUserId(), defaultSelection.action());
        var content = contentFor(selected.action());
        var explanation = reason == null || reason.isBlank() ? "최근 개인 기준에서 부담 신호가 감지됐어요." : reason;
        var body = explanation + " " + content.body() + (selected.personalized() ? " " + selected.rationale() : "");
        return dispatchAction(snapshot.getUserId(), selected.action(), defaultSelection.trigger(), explanation, confidence,
                content.title(), body, content.durationSeconds(), load, DeliveryKind.RECOVERY);
    }

    public DispatchResult sendAiRecoveryAlert(HealthSignalSnapshot snapshot, int load, String reason, String title, String body) {
        var defaultSelection = defaultActionFor(snapshot);
        var aiAction = actionFromCopy(title + " " + body, defaultSelection.action());
        var selected = personalization.personalizeProactiveAction(snapshot.getUserId(), aiAction);
        var fallbackContent = contentFor(selected.action());
        var useAiCopy = selected.action() == aiAction && body != null && !body.isBlank();
        var notificationTitle = useAiCopy ? cleanCopy(title, fallbackContent.title(), 80) : fallbackContent.title();
        var notificationBody = useAiCopy ? cleanCopy(body, fallbackContent.body(), 180) : fallbackContent.body();
        var explanation = reason == null || reason.isBlank() ? "최근 개인 기준에서 부담 신호가 감지됐어요." : reason;
        return dispatchAction(snapshot.getUserId(), selected.action(), "AI_" + defaultSelection.trigger(), explanation, "AI",
                notificationTitle, notificationBody, fallbackContent.durationSeconds(), load, DeliveryKind.RECOVERY);
    }

    public DispatchResult sendRecommendationAlert(Recommendation recommendation) {
        var action = recommendation.getAction();
        var content = contentFor(action);
        var title = cleanCopy(recommendation.getTitle(), content.title(), 80);
        var rationale = cleanCopy(recommendation.getRationale(), "지금 상태에 맞는 짧은 회복 행동이에요.", 150);
        var body = rationale + " 알림을 눌러 바로 시작할 수 있어요.";
        return dispatchAction(recommendation.getUserId(), action, "CHECK_IN_RECOMMENDATION", rationale,
                recommendation.getSource().name(), title, body, recommendation.getDurationSeconds(), null, DeliveryKind.RECOMMENDATION);
    }

    private DispatchResult dispatchAction(String userId, RecoveryAttempt.Action action, String trigger, String reason,
                                          String confidence, String title, String body, int durationSeconds,
                                          Integer load, DeliveryKind kind) {
        var attempt = recoveryAttempts.prepareSuggestion(userId, action, trigger, reason, confidence, RecoveryAttempt.Source.NOTIFICATION);
        var data = new LinkedHashMap<String, Object>();
        data.put("type", "RECOVERY");
        data.put("action", action.name());
        data.put("attemptId", attempt.getId().toString());
        data.put("reason", reason);
        data.put("confidence", confidence);
        data.put("durationSeconds", durationSeconds);
        if (load != null) data.put("load", load);
        var result = send(userId, title, body, "MORROW_ACTION", data, kind);
        if (result.accepted() > 0) recoveryAttempts.recordDeliveredSuggestion(attempt);
        return result;
    }

    private DefaultAction defaultActionFor(HealthSignalSnapshot snapshot) {
        if (snapshot.getSleepMinutes() != null && snapshot.getSleepMinutes() > 0 && snapshot.getSleepMinutes() < 360) {
            return new DefaultAction(RecoveryAttempt.Action.WATER_WALK, "SHORT_SLEEP");
        }
        if (snapshot.getSteps() != null && snapshot.getSteps() < 2500) {
            return new DefaultAction(RecoveryAttempt.Action.WALK, "LOW_ACTIVITY");
        }
        if (snapshot.getRestingHeartRate() != null && snapshot.getRestingHeartRate() > 78) {
            return new DefaultAction(RecoveryAttempt.Action.BREATH, "ELEVATED_RESTING_HEART_RATE");
        }
        if (snapshot.getExerciseMinutes() != null && snapshot.getExerciseMinutes() >= 45) {
            return new DefaultAction(RecoveryAttempt.Action.STRETCH, "POST_EXERCISE");
        }
        return new DefaultAction(RecoveryAttempt.Action.BREATH, "RECOVERY_LOAD");
    }

    private RecoveryAttempt.Action actionFromCopy(String copy, RecoveryAttempt.Action fallback) {
        var value = copy == null ? "" : copy;
        if (value.contains("물")) return RecoveryAttempt.Action.WATER_WALK;
        if (value.contains("걷") || value.contains("산책")) return RecoveryAttempt.Action.WALK;
        if (value.contains("스트레칭") || value.contains("어깨") || value.contains("목")) return RecoveryAttempt.Action.STRETCH;
        if (value.contains("집중") || value.contains("할 일")) return RecoveryAttempt.Action.FOCUS;
        if (value.contains("화면") || value.contains("눈을")) return RecoveryAttempt.Action.SCREEN_BREAK;
        if (value.contains("호흡") || value.contains("숨")) return RecoveryAttempt.Action.BREATH;
        return fallback;
    }

    private String cleanCopy(String value, String fallback, int maxLength) {
        var cleaned = value == null || value.isBlank() ? fallback : value.strip().replaceAll("\\s+", " ");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength).strip();
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
    private record DefaultAction(RecoveryAttempt.Action action, String trigger) {}
    private enum DeliveryKind { GENERAL, RECOVERY, RECOMMENDATION, CHECK_IN }
}
