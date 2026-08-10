package app.morrow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class DemoLoginService {
    private final DeviceAuthService deviceAuth;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String userId;

    public DemoLoginService(
            DeviceAuthService deviceAuth,
            @Value("${morrow.demo-login.enabled:false}") boolean enabled,
            @Value("${morrow.demo-login.username:}") String username,
            @Value("${morrow.demo-login.password:}") String password,
            @Value("${morrow.demo-login.user-id:hackathon-demo}") String userId
    ) {
        this.deviceAuth = deviceAuth;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.userId = userId;
    }

    public DeviceAuthService.Credentials login(
            String suppliedUsername,
            String suppliedPassword,
            String deviceId,
            String deviceName,
            DeviceSession.Platform platform
    ) {
        if (!enabled
                || !secureEquals(username, suppliedUsername == null ? "" : suppliedUsername.trim())
                || !secureEquals(password, suppliedPassword == null ? "" : suppliedPassword)) {
            throw new InvalidCredentialsException("사용자 이름 또는 비밀번호가 올바르지 않습니다.");
        }
        return deviceAuth.registerForUser(deviceId, deviceName, platform, userId);
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }
}
