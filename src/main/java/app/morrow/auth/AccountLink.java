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

    protected AccountLink() {}

    AccountLink(String accountId, String userId) {
        this.accountId = accountId;
        this.userId = userId;
        this.linkedAt = OffsetDateTime.now();
    }

    void linkTo(String userId) {
        this.userId = userId;
        this.linkedAt = OffsetDateTime.now();
    }

    public String getAccountId() { return accountId; }
    public String getUserId() { return userId; }
    public OffsetDateTime getLinkedAt() { return linkedAt; }
}
