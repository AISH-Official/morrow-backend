package app.morrow.api;

import app.morrow.auth.DeviceAuthService;
import app.morrow.auth.DeviceSession;
import app.morrow.auth.DemoLoginService;
import app.morrow.auth.AccountAuthService;
import app.morrow.auth.AuthRateLimiter;
import app.morrow.notification.PushNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final DeviceAuthService service;
    private final DemoLoginService demoLogin;
    private final AccountAuthService accountAuth;
    private final PushNotificationService notifications;
    public AuthController(DeviceAuthService service, DemoLoginService demoLogin, AccountAuthService accountAuth, PushNotificationService notifications) {
        this.service = service;
        this.demoLogin = demoLogin;
        this.accountAuth = accountAuth;
        this.notifications = notifications;
    }

    @PostMapping("/device") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse register(@Valid @RequestBody RegisterRequest request) {
        return CredentialsResponse.from(service.register(request.deviceId(), request.deviceName(), request.platform(), request.userId()));
    }

    @PostMapping("/pair") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse pair(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody PairRequest request) {
        return CredentialsResponse.from(accountAuth.pair(bearerToken(authorization), request.pairingCode(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @PostMapping("/account") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse account(@Valid @RequestBody AccountLoginRequest request) {
        return CredentialsResponse.from(accountAuth.login(request.accountId(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse signup(@Valid @RequestBody PasswordAccountRequest request) {
        return CredentialsResponse.from(accountAuth.signup(request.accountId(), request.password(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @PostMapping("/account-login") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse accountLogin(@Valid @RequestBody PasswordAccountRequest request) {
        return CredentialsResponse.from(accountAuth.loginWithPassword(request.accountId(), request.password(), request.deviceId(), request.deviceName(), request.platform()));
    }

    @PostMapping("/login") @ResponseStatus(HttpStatus.CREATED)
    CredentialsResponse login(@Valid @RequestBody LoginRequest request) {
        return CredentialsResponse.from(demoLogin.login(
                request.username(), request.password(), request.deviceId(), request.deviceName(), request.platform()
        ));
    }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(bearerToken(authorization));
    }

    @PostMapping("/pairing-code")
    CredentialsResponse refreshPairingCode(@RequestHeader("Authorization") String authorization, @RequestParam String deviceId) {
        var userId = service.authenticate(bearerToken(authorization));
        if (userId == null) throw new AccountAuthService.AccountLoginRequiredException("로그인이 필요합니다.");
        return CredentialsResponse.from(service.refreshPairingCode(userId, deviceId, bearerToken(authorization)));
    }

    @GetMapping("/devices") List<DeviceInfoResponse> devices(@RequestHeader("Authorization") String authorization) {
        var userId = service.authenticate(bearerToken(authorization));
        if (userId == null) throw new AccountAuthService.AccountLoginRequiredException("로그인이 필요합니다.");
        return service.devices(userId).stream().map(DeviceInfoResponse::from).toList();
    }

    @DeleteMapping("/devices/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeDevice(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        var userId = service.authenticate(bearerToken(authorization));
        if (userId == null) throw new AccountAuthService.AccountLoginRequiredException("로그인이 필요합니다.");
        service.revokeDevice(userId, id);
    }

    @ExceptionHandler(DeviceAuthService.InvalidPairingCodeException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse invalidCode(DeviceAuthService.InvalidPairingCodeException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(DemoLoginService.InvalidCredentialsException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidCredentials(DemoLoginService.InvalidCredentialsException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.InvalidAccountIdException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidAccount(AccountAuthService.InvalidAccountIdException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.AccountAlreadyLinkedException.class) @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse alreadyLinked(AccountAuthService.AccountAlreadyLinkedException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.AccountAlreadyExistsException.class) @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse accountExists(AccountAuthService.AccountAlreadyExistsException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.InvalidCredentialsException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidAccountCredentials(AccountAuthService.InvalidCredentialsException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.PasswordLoginRequiredException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse passwordRequired(AccountAuthService.PasswordLoginRequiredException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AccountAuthService.AccountLoginRequiredException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse accountLoginRequired(AccountAuthService.AccountLoginRequiredException error) { return new ErrorResponse(error.getMessage()); }

    @ExceptionHandler(AuthRateLimiter.TooManyAttemptsException.class) @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    ErrorResponse tooManyAttempts(AuthRateLimiter.TooManyAttemptsException error) { return new ErrorResponse(error.getMessage()); }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : null;
    }

    record RegisterRequest(@NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform, @Size(max=100) String userId) {}
    record PairRequest(@NotBlank @Size(max=8) String pairingCode, @NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform) {}
    record AccountLoginRequest(@NotBlank @Size(max=80) String accountId, @NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform) {}
    record PasswordAccountRequest(@NotBlank @Size(min=2,max=80) String accountId, @NotBlank @Size(min=8,max=120) String password, @NotBlank @Size(max=160) String deviceId, @NotBlank @Size(max=120) String deviceName, @NotNull DeviceSession.Platform platform) {}
    record LoginRequest(@NotBlank @Size(max=80) String username,@NotBlank @Size(max=120) String password,@NotBlank @Size(max=160) String deviceId,@NotBlank @Size(max=120) String deviceName,@NotNull DeviceSession.Platform platform) {}
    record CredentialsResponse(String userId, String accessToken, String pairingCode, String deviceId, String platform) {
        static CredentialsResponse from(DeviceAuthService.Credentials value) { return new CredentialsResponse(value.userId(), value.accessToken(), value.pairingCode(), value.deviceId(), value.platform().name()); }
    }
    record DeviceInfoResponse(UUID id,String deviceId,String deviceName,String platform,OffsetDateTime lastSeenAt){static DeviceInfoResponse from(DeviceAuthService.DeviceInfo value){return new DeviceInfoResponse(value.id(),value.deviceId(),value.deviceName(),value.platform().name(),value.lastSeenAt());}}
    record ErrorResponse(String message) {}
}
