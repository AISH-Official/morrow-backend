package app.morrow.recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, UUID> {
 List<RecommendationFeedback> findByRecommendationId(UUID recommendationId);
 void deleteByRecommendationIdIn(Collection<UUID> recommendationIds);
}
