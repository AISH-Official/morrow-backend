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
    private final DemoLoginService demoLogin;
    private final AccountPasswordHasher passwords;
    private final String demoAccountId;
    private final String demoUserId;
    private final AuthRateLimiter rateLimiter;

    public AccountAuthService(
            AccountLinkRepository accounts,
            DeviceAuthService devices,
            AuthRateLimiter rateLimiter,
            DemoLoginService demoLogin,
            AccountPasswordHasher passwords,
            @Value("${morrow.demo-login.username:}") String demoAccountId,
            @Value("${morrow.demo-login.user-id:hackathon-demo}") String demoUserId
    ) {
        this.accounts = accounts;
        this.devices = devices;
        this.rateLimiter = rateLimiter;
        this.demoLogin = demoLogin;
        this.passwords = passwords;
        this.demoAccountId = normalize(demoAccountId);
        this.demoUserId = demoUserId;
    }

    public DeviceAuthService.Credentials login(String accountId, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var normalized = normalize(accountId);
        rateLimiter.check("login:" + normalized + ":" + deviceId);
        if (normalized.isBlank()) throw new InvalidAccountIdException("아이디를 입력해 주세요.");
        var account = accounts.findById(normalized).orElseGet(() -> accounts.save(new AccountLink(normalized, initialUserId(normalized))));
        if (account.hasPassword()) throw new PasswordLoginRequiredException();
        return devices.registerForUser(deviceId, deviceName, platform, account.getUserId());
    }

    public DeviceAuthService.Credentials signup(String accountId, String password, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var normalized = normalize(accountId);
        rateLimiter.check("signup:" + normalized + ":" + deviceId);
        validateNewCredentials(normalized, password);
        if (!demoAccountId.isBlank() && demoAccountId.equals(normalized)) {
            throw new AccountAlreadyExistsException("기존 테스트 계정입니다. 로그인해 주세요.");
        }
        var existing = accounts.findById(normalized).orElse(null);
        if (existing != null) {
            if (existing.hasPassword()) throw new AccountAlreadyExistsException("이미 사용 중인 아이디입니다.");
            existing.setPasswordHash(passwords.hash(password));
            return devices.registerForUser(deviceId, deviceName, platform, existing.getUserId());
        }
        var account = accounts.save(new AccountLink(normalized, initialUserId(normalized), passwords.hash(password)));
        return devices.registerForUser(deviceId, deviceName, platform, account.getUserId());
    }

    public DeviceAuthService.Credentials loginWithPassword(String accountId, String password, String deviceId, String deviceName, DeviceSession.Platform platform) {
        var normalized = normalize(accountId);
        rateLimiter.check("password-login:" + normalized + ":" + deviceId);
        if (!demoAccountId.isBlank() && demoAccountId.equals(normalized)) {
            return demoLogin.login(accountId, password, deviceId, deviceName, platform);
        }
        var account = accounts.findById(normalized).orElseThrow(InvalidCredentialsException::new);
        if (!account.hasPassword() || !passwords.matches(password, account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return devices.registerForUser(deviceId, deviceName, platform, account.getUserId());
    }

    public DeviceAuthService.Credentials pair(String rawToken, String pairingCode, String deviceId, String deviceName, DeviceSession.Platform platform) {
        rateLimiter.check("pair:" + deviceId);
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

    private void validateNewCredentials(String accountId, String password) {
        if (accountId.length() < 2) throw new InvalidAccountIdException("아이디는 2자 이상 입력해 주세요.");
        if (!accountId.matches("[a-z0-9._\\-가-힣]+")) throw new InvalidAccountIdException("아이디에는 한글, 영문 소문자, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.");
        if (password == null || password.length() < 8) throw new InvalidAccountIdException("비밀번호는 8자 이상 입력해 주세요.");
    }

    @Transactional(readOnly = true)
    public boolean aiHealthConsent(String userId) { return accounts.findByUserId(userId).map(AccountLink::isAiHealthConsent).orElse(false); }

    public boolean updateAiHealthConsent(String userId, boolean consent) {
        var account = accounts.findByUserId(userId).orElseThrow(() -> new InvalidAccountIdException("연결된 계정을 찾을 수 없습니다."));
        account.setAiHealthConsent(consent);
        return consent;
    }
    public void deleteAccount(String userId) { devices.revokeAll(userId); accounts.deleteByUserId(userId); }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static class InvalidAccountIdException extends RuntimeException {
        public InvalidAccountIdException(String message) { super(message); }
    }

    public static class AccountAlreadyLinkedException extends RuntimeException {
        public AccountAlreadyLinkedException(String message) { super(message); }
    }

    public static class AccountAlreadyExistsException extends RuntimeException {
        public AccountAlreadyExistsException(String message) { super(message); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() { super("아이디 또는 비밀번호가 올바르지 않습니다."); }
    }

    public static class PasswordLoginRequiredException extends RuntimeException {
        public PasswordLoginRequiredException() { super("비밀번호로 로그인해 주세요."); }
    }

    public static class AccountLoginRequiredException extends RuntimeException {
        public AccountLoginRequiredException(String message) { super(message); }
    }
}
