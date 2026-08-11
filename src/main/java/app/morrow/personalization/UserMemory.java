package app.morrow.personalization;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "user_memories",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_memory_key", columnNames = {"userId", "memoryKey"}),
    indexes = @Index(name = "idx_user_memory_active", columnList = "userId,active")
)
public class UserMemory {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Type type;
    @Column(nullable = false, length = 180) private String memoryKey;
    @Column(nullable = false, length = 600) private String summary;
    private int positiveEvidence;
    private int negativeEvidence;
    private double confidence;
    private boolean active;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Source source;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected UserMemory() {}

    public UserMemory(String userId, Type type, String memoryKey, String summary, Source source) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.memoryKey = memoryKey;
        this.summary = summary;
        this.source = source;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
        recalculateConfidence();
    }

    public void observe(String summary, boolean positive, Source source) {
        this.summary = summary;
        this.source = source;
        if (positive) positiveEvidence++; else negativeEvidence++;
        this.active = true;
        this.updatedAt = OffsetDateTime.now();
        recalculateConfidence();
    }

    public void correct(String summary, Boolean active) {
        if (summary != null && !summary.isBlank()) this.summary = summary;
        if (active != null) this.active = active;
        this.source = Source.USER_DECLARED;
        this.updatedAt = OffsetDateTime.now();
        recalculateConfidence();
    }

    private void recalculateConfidence() {
        var evidence = positiveEvidence + negativeEvidence;
        this.confidence = source == Source.USER_DECLARED ? 1.0 : Math.min(0.95, 0.35 + evidence * 0.12);
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public Type getType() { return type; }
    public String getMemoryKey() { return memoryKey; }
    public String getSummary() { return summary; }
    public int getPositiveEvidence() { return positiveEvidence; }
    public int getNegativeEvidence() { return negativeEvidence; }
    public int getEvidenceCount() { return positiveEvidence + negativeEvidence; }
    public double getConfidence() { return confidence; }
    public boolean isActive() { return active; }
    public Source getSource() { return source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public enum Type { TRIGGER_PATTERN, RECOVERY_STRATEGY, PREFERENCE, GOAL }
    public enum Source { CHECK_IN_PATTERN, RECOMMENDATION_FEEDBACK, RECOVERY_OUTCOME, USER_DECLARED }
}
