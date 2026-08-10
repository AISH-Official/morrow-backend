package app.morrow.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;

@Service
@Transactional
public class PushNotificationService {
    private final PushDeviceRepository repository;
    private final ApnsGateway gateway;
    private final ApnsProperties properties;

    public PushNotificationService(PushDeviceRepository repository, ApnsGateway gateway, ApnsProperties properties) { this.repository = repository; this.gateway = gateway; this.properties = properties; }

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
        return send(userId, "회복 신호가 높아요", "지금 1분 호흡이나 7분 걷기로 리듬을 낮춰보세요.", "MORROW_RECOVERY", Map.of("type", "RECOVERY", "load", load), true);
    }

    @Transactional(readOnly = true) public Status status() { return new Status(properties.isEnabled(), properties.ready(), repository.countByActiveTrue(), properties.getIosTopic(), properties.getWatchTopic()); }
    public record DispatchResult(int attempted, long accepted, java.util.List<DeviceResult> devices) {}
    public record DeviceResult(PushDevice.Platform platform, boolean accepted, int statusCode, String reason) {}
    public record Status(boolean enabled, boolean ready, long activeDevices, String iosTopic, String watchTopic) {}
}
