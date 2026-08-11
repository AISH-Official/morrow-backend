package app.morrow.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;

@Service
@Transactional
public class PushNotificationService {
    private final PushDeviceRepository repository;
    private final ApnsGateway gateway;
    private final ApnsProperties properties;
    private final ZoneId timeZone;

    public PushNotificationService(PushDeviceRepository repository, ApnsGateway gateway, ApnsProperties properties, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone) { this.repository = repository; this.gateway = gateway; this.properties = properties; this.timeZone = ZoneId.of(timeZone); }

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
        var results = new ArrayList<DeviceResult>(); var now = OffsetDateTime.now();
        var localHour = now.atZoneSameInstant(timeZone).getHour();
        if (respectCooldown && (localHour >= 22 || localHour < 8)) return new DispatchResult(0, 0, java.util.List.of());
        for (var device : repository.findByUserIdAndActiveTrue(userId)) {
            if (respectCooldown && device.getLastNotifiedAt() != null && device.getLastNotifiedAt().isAfter(now.minusHours(6))) { results.add(new DeviceResult(device.getPlatform(), false, 429, "Cooldown")); continue; }
            var result = gateway.send(device, title, body, category, data);
            if (result.accepted()) device.markNotified();
            if (result.statusCode() == 410 || "BadDeviceToken".equals(result.reason()) || "Unregistered".equals(result.reason())) device.deactivate();
            results.add(new DeviceResult(device.getPlatform(), result.accepted(), result.statusCode(), result.reason()));
        }
        return new DispatchResult(results.size(), results.stream().filter(DeviceResult::accepted).count(), results);
    }

    public DispatchResult sendRecoveryAlert(String userId, int load) {
        return send(userId, "지금 1분만 호흡해요", "손목에서 시작하고 4초 들이마신 뒤 6초 내쉬기를 6번 반복해 보세요.", "MORROW_ACTION", Map.of("type", "RECOVERY", "action", "BREATH", "load", load), true);
    }

    public DispatchResult sendActionableRecoveryAlert(HealthSignalSnapshot snapshot, int load) {
        var title = "지금 1분만 호흡해요";
        var body = "4초 들이마시고 6초 내쉬기를 6번 반복해 보세요.";
        var action = "BREATH";
        if (snapshot.getSleepMinutes() != null && snapshot.getSleepMinutes() > 0 && snapshot.getSleepMinutes() < 360) {
            title = "지금 물 한 잔 어때요?";
            body = "수면이 짧았어요. 물을 마시고 5분만 천천히 걸어 몸을 깨워보세요.";
            action = "WATER_WALK";
        } else if (snapshot.getSteps() != null && snapshot.getSteps() < 2500) {
            title = "지금 5분 걸어볼까요?";
            body = "오늘 움직임이 적어요. 자리에서 일어나 가까운 곳까지 가볍게 걸어보세요.";
            action = "WALK";
        } else if (snapshot.getRestingHeartRate() != null && snapshot.getRestingHeartRate() > 78) {
            title = "어깨 힘부터 빼볼까요?";
            body = "편한 자세로 바꾸고 길게 내쉬는 호흡을 1분만 시작해 보세요.";
            action = "BREATH";
        } else if (snapshot.getExerciseMinutes() != null && snapshot.getExerciseMinutes() >= 45) {
            title = "오늘은 회복을 먼저 챙겨요";
            body = "움직임은 충분했어요. 3분 스트레칭하고 물 한 잔으로 마무리해 보세요.";
            action = "STRETCH";
        }
        return send(snapshot.getUserId(), title, body, "MORROW_ACTION", Map.of("type", "RECOVERY", "action", action, "load", load), true);
    }

    @Transactional(readOnly = true) public Status status() { return new Status(properties.isEnabled(), properties.ready(), repository.countByActiveTrue(), properties.getIosTopic(), properties.getWatchTopic()); }
    public record DispatchResult(int attempted, long accepted, java.util.List<DeviceResult> devices) {}
    public record DeviceResult(PushDevice.Platform platform, boolean accepted, int statusCode, String reason) {}
    public record Status(boolean enabled, boolean ready, long activeDevices, String iosTopic, String watchTopic) {}
}
