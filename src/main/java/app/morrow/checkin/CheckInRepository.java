package app.morrow.checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {
 List<CheckIn> findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(String userId,OffsetDateTime after);
 @Query("SELECT c.status, COUNT(c) FROM CheckIn c WHERE c.userId = :userId AND c.recordedAt > :after GROUP BY c.status ORDER BY COUNT(c) DESC")
 List<Object[]> countByStatusAfter(String userId,OffsetDateTime after);
 @Query("SELECT c.cause, COUNT(c) FROM CheckIn c WHERE c.userId = :userId AND c.recordedAt > :after AND c.cause IS NOT NULL GROUP BY c.cause ORDER BY COUNT(c) DESC")
 List<Object[]> countByCauseAfter(String userId,OffsetDateTime after);
 void deleteByUserId(String userId);
}
