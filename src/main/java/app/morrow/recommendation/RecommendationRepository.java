package app.morrow.recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
 Optional<Recommendation> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId,Recommendation.Status status);
 List<Recommendation> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(String userId,OffsetDateTime after);
 List<Recommendation> findByUserId(String userId);
 List<Recommendation> findByUserIdAndCheckInId(String userId,UUID checkInId);
 void deleteByUserId(String userId);
}
