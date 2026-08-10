package app.morrow.api;

import app.morrow.auth.DeviceAuthService;
import app.morrow.auth.DeviceSession;
import app.morrow.auth.DemoLoginService;
import app.morrow.notification.PushNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final DeviceAuthService service;
    private final DemoLoginService demoLogin;
    private final PushNotificationService notifications;
    public AuthController(DeviceAuthService service, DemoLoginService demoLogin, PushNotificationService notifications) {
        this.service = service;
        this.demoLogin = demoLogin;
        this.notifications = notifications;
    }

    @PostMapping("/device") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse register(@Valid @RequestBody RegisterRequest request) {
        return CredentialsResponse.from(service.register(request.deviceId(), request.deviceName(), request.platform(), request.userId()));
    }

    @PostMapping("/pair") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse pair(@Valid @RequestBody PairRequest request) {
        return CredentialsResponse.from(service.pair(request.pairingCode(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @PostMapping("/login") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse login(@Valid @RequestBody LoginRequest request) {
        return CredentialsResponse.from(demoLogin.login(
                request.username(), request.password(), request.deviceId(), request.deviceName(), request.platform()
        ));
    }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        var session = service.logout(bearerToken(authorization));
        if (session != null && session.platform() != DeviceSession.Platform.WEB) {
            notifications.unregisterAll(session.userId());
        }
    }

    @ExceptionHandler(DeviceAuthService.InvalidPairingCodeException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse invalidCode(DeviceAuthService.InvalidPairingCodeException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(DemoLoginService.InvalidCredentialsException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidCredentials(DemoLoginService.InvalidCredentialsException error) { return new ErrorResponse(error.getMessage()); }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : null;
    }

    record RegisterRequest(@NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform, @Size(max=100) String userId) {}
    record PairRequest(@NotBlank @Size(max=8) String pairingCode, @NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform) {}
    record LoginRequest(@NotBlank @Size(max=80) String username,@NotBlank @Size(max=120) String password,@NotBlank @Size(max=160) String deviceId,@NotBlank @Size(max=120) String deviceName,@NotNull DeviceSession.Platform platform) {}
    record CredentialsResponse(String userId, String accessToken, String pairingCode, String deviceId, String platform) {
        static CredentialsResponse from(DeviceAuthService.Credentials value) { return new CredentialsResponse(value.userId(), value.accessToken(), value.pairingCode(), value.deviceId(), value.platform().name()); }
    }
    record ErrorResponse(String message) {}
}
