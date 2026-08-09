package app.morrow.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "device_sessions", indexes = {
        @Index(name = "idx_device_session_user", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_session_device", columnNames = "device_id"),
        @UniqueConstraint(name = "uk_device_session_token", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_device_session_pairing", columnNames = "pairing_code")
})
public class DeviceSession {
    @Id private UUID id;
    @Column(name = "device_id", nullable = false, length = 160) private String deviceId;
    @Column(name = "device_name", nullable = false, length = 120) private String deviceName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Platform platform;
    @Column(name = "user_id", nullable = false, length = 100) private String userId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "pairing_code", nullable = false, length = 8) private String pairingCode;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "last_seen_at", nullable = false) private OffsetDateTime lastSeenAt;

    protected DeviceSession() {}

    DeviceSession(String deviceId, String deviceName, Platform platform, String userId, String tokenHash, String pairingCode) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.platform = platform;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.pairingCode = pairingCode;
        this.createdAt = OffsetDateTime.now();
        this.lastSeenAt = this.createdAt;
    }

    void rotate(String deviceName, Platform platform, String tokenHash, String pairingCode, String userId) {
        this.deviceName = deviceName;
        this.platform = platform;
        this.tokenHash = tokenHash;
        this.pairingCode = pairingCode;
        this.userId = userId;
        this.lastSeenAt = OffsetDateTime.now();
    }

    void touch() { this.lastSeenAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public Platform getPlatform() { return platform; }
    public String getUserId() { return userId; }
    public String getPairingCode() { return pairingCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }

    public enum Platform { IOS, WATCHOS, WEB }
}
