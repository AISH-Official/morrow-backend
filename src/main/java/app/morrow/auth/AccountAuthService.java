package app.morrow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AccountAuthService {
    private final AccountLinkRepository accounts;
    private final DeviceAuthService devices;
    private final String demoAccountId;
    private final String demoUserId;

    public AccountAuthService(
            AccountLinkRepository accounts,
            DeviceAuthService devices,
            @Value("${morrow.demo-login.username:}") String demoAccountId,
            @Value("${morrow.demo-login.user-id:hackathon-demo}") String demoUserId
    ) {
        this.accounts = accounts;
        this.devices = devices;
        this.demoAccountId = normalize(demoAccountId);
        this.demoUserId = demoUserId;
    }

    public DeviceAuthService.Credentials login(String accountId, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var normalized = normalize(accountId);
        if (normalized.isBlank()) throw new InvalidAccountIdException("아이디를 입력해 주세요.");
        var account = accounts.findById(normalized).orElseGet(() -> accounts.save(new AccountLink(normalized, initialUserId(normalized))));
        return devices.registerForUser(deviceId, deviceName, platform, account.getUserId());
    }

    public DeviceAuthService.Credentials pair(String rawToken, String pairingCode, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var currentUserId = devices.authenticate(rawToken);
        if (currentUserId == null) throw new AccountLoginRequiredException("아이디로 로그인한 뒤 설정에서 연결해 주세요.");
        var credentials = devices.pair(pairingCode, deviceId, deviceName, platform);
        linkCurrentAccount(currentUserId, credentials.userId());
        return credentials;
    }

    private void linkCurrentAccount(String currentUserId, String targetUserId) {
        var current = accounts.findByUserId(currentUserId).orElse(null);
        if (current == null || current.getUserId().equals(targetUserId)) return;
        var owner = accounts.findByUserId(targetUserId).orElse(null);
        if (owner != null && !owner.getAccountId().equals(current.getAccountId())) {
            throw new AccountAlreadyLinkedException("이 기기는 다른 아이디에 이미 연결되어 있습니다.");
        }
        current.linkTo(targetUserId);
    }

    private String initialUserId(String accountId) {
        return !demoAccountId.isBlank() && demoAccountId.equals(accountId) ? demoUserId : "account-" + UUID.randomUUID();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static class InvalidAccountIdException extends RuntimeException {
        public InvalidAccountIdException(String message) { super(message); }
    }

    public static class AccountAlreadyLinkedException extends RuntimeException {
        public AccountAlreadyLinkedException(String message) { super(message); }
    }

    public static class AccountLoginRequiredException extends RuntimeException {
        public AccountLoginRequiredException(String message) { super(message); }
    }
}
