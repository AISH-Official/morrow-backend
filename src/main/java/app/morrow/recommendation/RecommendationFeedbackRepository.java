package app.morrow.recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, UUID> {
 List<RecommendationFeedback> findByRecommendationId(UUID recommendationId);
 @Query("""
  select feedback from RecommendationFeedback feedback
  where feedback.recommendationId in (
   select recommendation.id from Recommendation recommendation where recommendation.userId = :userId
  )
  and feedback.createdAt >= :after
  order by feedback.createdAt desc
  """)
 List<RecommendationFeedback> findForUserAfter(@Param("userId") String userId,@Param("after") OffsetDateTime after);
 void deleteByRecommendationIdIn(Collection<UUID> recommendationIds);
}
