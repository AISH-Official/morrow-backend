package app.morrow.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(name = "account_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_link_user", columnNames = "user_id")
})
public class AccountLink {
    @Id @Column(name = "account_id", length = 80) private String accountId;
    @Column(name = "user_id", nullable = false, length = 100) private String userId;
    @Column(name = "linked_at", nullable = false) private OffsetDateTime linkedAt;
    @Column(name = "ai_health_consent") private Boolean aiHealthConsent;
    @Column(name = "password_hash", length = 512) private String passwordHash;

    protected AccountLink() {}

    AccountLink(String accountId, String userId) {
        this(accountId, userId, null);
    }

    AccountLink(String accountId, String userId, String passwordHash) {
        this.accountId = accountId;
        this.userId = userId;
        this.linkedAt = OffsetDateTime.now();
        this.aiHealthConsent = false;
        this.passwordHash = passwordHash;
    }

    void linkTo(String userId) {
        this.userId = userId;
        this.linkedAt = OffsetDateTime.now();
    }

    public String getAccountId() { return accountId; }
    public String getUserId() { return userId; }
    public OffsetDateTime getLinkedAt() { return linkedAt; }
    public String getPasswordHash() { return passwordHash; }
    public boolean hasPassword() { return passwordHash != null && !passwordHash.isBlank(); }
    void setPasswordHash(String value) { this.passwordHash = value; }
    public boolean isAiHealthConsent() { return Boolean.TRUE.equals(aiHealthConsent); }
    public void setAiHealthConsent(boolean value) { this.aiHealthConsent = value; }
}
