package app.morrow.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, UUID> {
    List<RecoveryAttempt> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
    List<RecoveryAttempt> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(String userId, OffsetDateTime after);
    void deleteByUserId(String userId);
}
