package app.morrow.recommendation;
import app.morrow.personalization.PersonalizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service @Transactional
public class RecommendationService {
 private final RecommendationRepository recommendationRepository; private final RecommendationFeedbackRepository feedbackRepository; private final PersonalizationService personalizationService;
 public RecommendationService(RecommendationRepository recommendationRepository,RecommendationFeedbackRepository feedbackRepository,PersonalizationService personalizationService){this.recommendationRepository=recommendationRepository;this.feedbackRepository=feedbackRepository;this.personalizationService=personalizationService;}
 public Recommendation create(CreateRecommendation input){return recommendationRepository.save(new Recommendation(input.userId(),input.title(),input.rationale(),input.status()));}
 public RecommendationFeedback createFeedback(UUID recommendationId,CreateFeedback input){var recommendation=recommendationRepository.findById(recommendationId).orElseThrow(()->new RecommendationNotFoundException(recommendationId));var saved=feedbackRepository.save(new RecommendationFeedback(recommendationId,input.completed(),input.helpful(),input.note()));recommendation.applyFeedback(input.completed(),input.helpful());personalizationService.learnFromRecommendation(recommendation,input.helpful());return saved;}
 public Optional<Recommendation> findActiveByUserId(String userId){return recommendationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId,Recommendation.Status.ACTIVE);}
 public List<Recommendation> findRecentByUserId(String userId,OffsetDateTime after){return recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId,after);}
 public record CreateRecommendation(String userId,String title,String rationale,Recommendation.Status status){}
 public record CreateFeedback(boolean completed,boolean helpful,String note){}
 public static class RecommendationNotFoundException extends RuntimeException{public RecommendationNotFoundException(UUID id){super("Recommendation not found: "+id);}}
}
