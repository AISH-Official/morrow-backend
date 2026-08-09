package app.morrow.api;

import app.morrow.auth.DeviceAuthService;
import app.morrow.auth.DeviceSession;
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
    public AuthController(DeviceAuthService service) { this.service = service; }

    @PostMapping("/device") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse register(@Valid @RequestBody RegisterRequest request) {
        return CredentialsResponse.from(service.register(request.deviceId(), request.deviceName(), request.platform(), request.userId()));
    }

    @PostMapping("/pair") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse pair(@Valid @RequestBody PairRequest request) {
        return CredentialsResponse.from(service.pair(request.pairingCode(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @ExceptionHandler(DeviceAuthService.InvalidPairingCodeException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse invalidCode(DeviceAuthService.InvalidPairingCodeException error) { return new ErrorResponse(error.getMessage()); }

    record RegisterRequest(@NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform, @Size(max=100) String userId) {}
    record PairRequest(@NotBlank @Size(max=8) String pairingCode, @NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform) {}
    record CredentialsResponse(String userId, String accessToken, String pairingCode, String deviceId, String platform) {
        static CredentialsResponse from(DeviceAuthService.Credentials value) { return new CredentialsResponse(value.userId(), value.accessToken(), value.pairingCode(), value.deviceId(), value.platform().name()); }
    }
    record ErrorResponse(String message) {}
}
