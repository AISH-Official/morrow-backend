package app.morrow.recovery;

import app.morrow.personalization.PersonalizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RecoveryAttemptService {
    private final RecoveryAttemptRepository attempts;
    private final PersonalizationService personalization;

    public RecoveryAttemptService(RecoveryAttemptRepository attempts, PersonalizationService personalization) {
        this.attempts = attempts;
        this.personalization = personalization;
    }

    public RecoveryAttempt suggest(String userId, RecoveryAttempt.Action action, String trigger, String reason, String confidence, RecoveryAttempt.Source source) {
        return attempts.save(new RecoveryAttempt(normalize(userId), action, trigger, reason, confidence, source));
    }

    public RecoveryAttempt createAndStart(String userId, RecoveryAttempt.Action action, String trigger, String reason, String confidence, RecoveryAttempt.Source source) {
        var value = suggest(userId, action, trigger, reason, confidence, source);
        value.start();
        return value;
    }

    public RecoveryAttempt start(UUID id, String userId) {
        var value = findOwned(id, userId);
        value.start();
        return value;
    }

    public RecoveryAttempt complete(UUID id, String userId, RecoveryAttempt.Outcome outcome) {
        var value = findOwned(id, userId);
        value.complete(outcome);
        personalization.learnFromRecoveryOutcome(value.getUserId(), value.getAction(), outcome);
        return value;
    }

    @Transactional(readOnly = true)
    public List<RecoveryAttempt> recent(String userId) { return attempts.findTop20ByUserIdOrderByCreatedAtDesc(normalize(userId)); }

    @Transactional(readOnly = true)
    public List<RecoveryAttempt> after(String userId, OffsetDateTime after) { return attempts.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(normalize(userId), after); }

    public void deleteAll(String userId) { attempts.deleteByUserId(normalize(userId)); }

    private RecoveryAttempt findOwned(UUID id, String userId) {
        var normalized = normalize(userId);
        return attempts.findById(id).filter(value -> value.getUserId().equals(normalized)).orElseThrow(() -> new RecoveryAttemptNotFoundException(id));
    }

    private String normalize(String userId) { return userId == null || userId.isBlank() ? "default-user" : userId; }
    public static class RecoveryAttemptNotFoundException extends RuntimeException { public RecoveryAttemptNotFoundException(UUID id) { super("Recovery attempt not found: " + id); } }
}
