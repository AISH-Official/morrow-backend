package app.morrow.timeline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public interface TimelineRepository extends JpaRepository<Timeline, UUID> {
 List<Timeline> findByUserIdAndCreatedAtAfterOrderByTimeAsc(String userId,OffsetDateTime after);
 void deleteByUserId(String userId);
}
