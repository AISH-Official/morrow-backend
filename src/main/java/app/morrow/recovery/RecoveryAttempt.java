package app.morrow.recovery;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recovery_attempts", indexes = {
        @Index(name = "idx_recovery_user_created", columnList = "userId,createdAt"),
        @Index(name = "idx_recovery_user_status", columnList = "userId,status")
})
public class RecoveryAttempt {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Action action;
    @Column(nullable = false, length = 80) private String triggerType;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, length = 24) private String confidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Source source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Enumerated(EnumType.STRING) @Column(length = 24) private Outcome outcome;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    protected RecoveryAttempt() {}

    public RecoveryAttempt(String userId, Action action, String triggerType, String reason, String confidence, Source source) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = action;
        this.triggerType = clean(triggerType, "USER_STARTED");
        this.reason = clean(reason, "사용자가 회복 행동을 시작했어요.");
        this.confidence = clean(confidence, "LOW");
        this.source = source;
        this.status = Status.SUGGESTED;
        this.createdAt = OffsetDateTime.now();
    }

    public void start() {
        if (status == Status.COMPLETED) return;
        status = Status.STARTED;
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    public void complete(Outcome value) {
        if (startedAt == null) startedAt = OffsetDateTime.now();
        status = Status.COMPLETED;
        outcome = value;
        completedAt = OffsetDateTime.now();
    }

    private String clean(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public Action getAction() { return action; }
    public String getTriggerType() { return triggerType; }
    public String getReason() { return reason; }
    public String getConfidence() { return confidence; }
    public Source getSource() { return source; }
    public Status getStatus() { return status; }
    public Outcome getOutcome() { return outcome; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    public enum Action { BREATH, WALK, WATER_WALK, STRETCH, FOCUS, SCREEN_BREAK }
    public enum Source { NOTIFICATION, WATCH, IPHONE, WEB, DEMO }
    public enum Status { SUGGESTED, STARTED, COMPLETED }
    public enum Outcome { IMPROVED, SAME, WORSE }
}
