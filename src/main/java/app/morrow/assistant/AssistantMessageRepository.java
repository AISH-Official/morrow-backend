package app.morrow.assistant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, UUID> {
 List<AssistantMessage> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(String userId,OffsetDateTime after);
 List<AssistantMessage> findTop12ByUserIdOrderByCreatedAtDesc(String userId);
 void deleteByUserId(String userId);
}
