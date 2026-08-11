package app.morrow.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class DeviceAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final DeviceSessionRepository repository;

    public DeviceAuthService(DeviceSessionRepository repository) { this.repository = repository; }

    public Credentials register(String deviceId, String deviceName, DeviceSession.Platform platform) {
        var existing = repository.findByDeviceId(deviceId).orElse(null);
        if (existing != null) throw new DeviceAlreadyRegisteredException("이미 등록된 기기입니다. 계정으로 로그인해 주세요.");
        return saveRotated(null, deviceId, deviceName, platform, "user-" + UUID.randomUUID());
    }

    public Credentials registerForUser(String deviceId, String deviceName, DeviceSession.Platform platform, String userId) {
        var normalizedUserId = normalizeUserId(userId);
        if (normalizedUserId == null) throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        var existing = repository.findByDeviceId(deviceId).orElse(null);
        return saveRotated(existing, deviceId, deviceName, platform, normalizedUserId);
    }

    public Credentials pair(String pairingCode, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var owner = repository.findByPairingCode(normalizeCode(pairingCode))
                .filter(DeviceSession::isPairingCodeValid)
                .orElseThrow(() -> new InvalidPairingCodeException("유효하지 않은 연결 코드입니다."));
        var existing = repository.findByDeviceId(deviceId).orElse(null);
        return saveRotated(existing, deviceId, deviceName, platform, owner.getUserId());
    }

    public String authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        var session = repository.findByTokenHash(hash(rawToken)).orElse(null);
        if (session == null) return null;
        if (session.getLastSeenAt().isBefore(java.time.OffsetDateTime.now().minusMinutes(5))) session.touch();
        return session.getUserId();
    }

    public LoggedOutSession logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        var session = repository.findByTokenHash(hash(rawToken)).orElse(null);
        if (session == null) return null;
        var result = new LoggedOutSession(session.getUserId(), session.getPlatform());
        repository.delete(session);
        return result;
    }

    public Credentials refreshPairingCode(String userId, String deviceId, String rawToken) {
        var session = repository.findByDeviceId(deviceId)
                .filter(value -> value.getUserId().equals(userId))
                .orElseThrow(() -> new InvalidPairingCodeException("기기를 찾을 수 없습니다."));
        var code = uniquePairingCode();
        session.refreshPairingCode(code);
        return credentials(session, rawToken);
    }

    @Transactional(readOnly = true)
    public java.util.List<DeviceInfo> devices(String userId) {
        return repository.findByUserIdOrderByLastSeenAtDesc(userId).stream().map(value -> new DeviceInfo(value.getId(), value.getDeviceId(), value.getDeviceName(), value.getPlatform(), value.getLastSeenAt())).toList();
    }

    public void revokeDevice(String userId, UUID id) {
        var value = repository.findById(id).filter(item -> item.getUserId().equals(userId)).orElseThrow(() -> new DeviceNotFoundException(id));
        repository.delete(value);
    }
    public void revokeAll(String userId) { repository.deleteByUserId(userId); }

    private Credentials saveRotated(DeviceSession existing, String deviceId, String deviceName, DeviceSession.Platform platform, String userId) {
        var token = newToken();
        var code = uniquePairingCode();
        var session = existing == null
                ? new DeviceSession(deviceId, deviceName, platform, userId, hash(token), code)
                : existing;
        if (existing != null) session.rotate(deviceName, platform, hash(token), code, userId);
        repository.save(session);
        return credentials(session, token);
    }

    private Credentials credentials(DeviceSession session, String token) {
        return new Credentials(session.getUserId(), token, session.getPairingCode(), session.getDeviceId(), session.getPlatform());
    }

    private String uniquePairingCode() {
        for (var attempt = 0; attempt < 20; attempt++) {
            var builder = new StringBuilder(6);
            for (var index = 0; index < 6; index++) builder.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
            var value = builder.toString();
            if (repository.findByPairingCode(value).isEmpty()) return value;
        }
        throw new IllegalStateException("연결 코드를 만들 수 없습니다.");
    }

    private String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeUserId(String value) {
        if (value == null || value.isBlank() || "default-user".equals(value)) return null;
        return value.trim();
    }

    private String normalizeCode(String value) { return value == null ? "" : value.replace("-", "").trim().toUpperCase(Locale.ROOT); }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public record Credentials(String userId, String accessToken, String pairingCode, String deviceId, DeviceSession.Platform platform) {}
    public record LoggedOutSession(String userId, DeviceSession.Platform platform) {}
    public record DeviceInfo(UUID id, String deviceId, String deviceName, DeviceSession.Platform platform, java.time.OffsetDateTime lastSeenAt) {}
    public static class InvalidPairingCodeException extends RuntimeException { public InvalidPairingCodeException(String message) { super(message); } }
    public static class DeviceAlreadyRegisteredException extends RuntimeException { public DeviceAlreadyRegisteredException(String message) { super(message); } }
    public static class DeviceNotFoundException extends RuntimeException { public DeviceNotFoundException(UUID id) { super("Device not found: " + id); } }
}
