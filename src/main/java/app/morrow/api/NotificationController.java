package app.morrow.api;

import app.morrow.auth.RequestUserResolver;
import app.morrow.notification.PushDevice;
import app.morrow.notification.PushNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final PushNotificationService service; private final RequestUserResolver users;
    public NotificationController(PushNotificationService service, RequestUserResolver users) { this.service = service; this.users = users; }

    @PostMapping("/devices") @ResponseStatus(HttpStatus.CREATED)
    DeviceResponse register(@Valid @RequestBody DeviceRequest request) {
        var value = service.register(users.resolve(request.userId()), request.deviceToken(), request.platform(), request.environment());
        return DeviceResponse.from(value);
    }

    @DeleteMapping("/devices") @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@RequestParam(defaultValue = "default-user") String userId, @RequestParam String deviceToken) { service.unregister(users.resolve(userId), deviceToken); }

    @PostMapping("/test") PushNotificationService.DispatchResult test(@RequestParam(defaultValue = "default-user") String userId) {
        return service.send(users.resolve(userId), "Morrow 알림 연결 완료", "iPhone과 Apple Watch 푸시 채널이 정상적으로 연결됐어요.", "MORROW_TEST", Map.of("type", "TEST"), false);
    }

    @GetMapping("/status") PushNotificationService.Status status() { return service.status(); }

    record DeviceRequest(@Size(max=100) String userId, @NotBlank @Size(max=256) String deviceToken, @NotNull PushDevice.Platform platform, @NotNull PushDevice.Environment environment) {}
    record DeviceResponse(UUID id, String userId, String platform, String environment, boolean active) { static DeviceResponse from(PushDevice value) { return new DeviceResponse(value.getId(), value.getUserId(), value.getPlatform().name(), value.getEnvironment().name(), value.isActive()); } }
}
