package app.morrow.notification;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_devices", uniqueConstraints = @UniqueConstraint(name = "uk_push_device_token", columnNames = "device_token"), indexes = @Index(name = "idx_push_device_user", columnList = "user_id"))
public class PushDevice {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, length = 100) private String userId;
    @Column(name = "device_token", nullable = false, length = 256) private String deviceToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Platform platform;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Environment environment;
    @Column(nullable = false) private boolean active;
    @Column(name = "registered_at", nullable = false) private OffsetDateTime registeredAt;
    @Column(name = "last_seen_at", nullable = false) private OffsetDateTime lastSeenAt;
    @Column(name = "last_notified_at") private OffsetDateTime lastNotifiedAt;

    protected PushDevice() {}
    PushDevice(String userId, String deviceToken, Platform platform, Environment environment) {
        this.id = UUID.randomUUID(); this.userId = userId; this.deviceToken = deviceToken; this.platform = platform; this.environment = environment;
        this.active = true; this.registeredAt = OffsetDateTime.now(); this.lastSeenAt = this.registeredAt;
    }
    void refresh(String userId, Platform platform, Environment environment) { this.userId = userId; this.platform = platform; this.environment = environment; this.active = true; this.lastSeenAt = OffsetDateTime.now(); }
    void deactivate() { this.active = false; }
    void markNotified() { this.lastNotifiedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; } public String getUserId() { return userId; } public String getDeviceToken() { return deviceToken; }
    public Platform getPlatform() { return platform; } public Environment getEnvironment() { return environment; } public boolean isActive() { return active; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; } public OffsetDateTime getLastSeenAt() { return lastSeenAt; } public OffsetDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public enum Platform { IOS, WATCHOS }
    public enum Environment { SANDBOX, PRODUCTION }
}
