package app.morrow.recommendation;
import app.morrow.personalization.PersonalizationService;
import app.morrow.recovery.RecoveryAttempt;
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
 public Recommendation create(CreateRecommendation input){var value=new Recommendation(input.userId(),input.title(),input.rationale(),input.status(),input.action(),input.durationSeconds(),input.source());if(input.checkInId()!=null)value.linkToCheckIn(input.checkInId());return recommendationRepository.save(value);}
 public RecommendationFeedback createFeedback(UUID recommendationId,String userId,CreateFeedback input){var recommendation=recommendationRepository.findById(recommendationId).filter(value->"default-user".equals(userId)||value.getUserId().equals(userId)).orElseThrow(()->new RecommendationNotFoundException(recommendationId));var saved=feedbackRepository.save(new RecommendationFeedback(recommendationId,input.completed(),input.helpful(),input.note()));recommendation.applyFeedback(input.completed(),input.helpful());personalizationService.learnFromRecommendation(recommendation,input.helpful());return saved;}
 public Optional<Recommendation> findActiveByUserId(String userId){return recommendationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId,Recommendation.Status.ACTIVE);}
 public List<Recommendation> findRecentByUserId(String userId,OffsetDateTime after){return recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId,after);}
 public void deleteForCheckIn(String userId,UUID checkInId){var values=recommendationRepository.findByUserIdAndCheckInId(userId,checkInId);var ids=values.stream().map(Recommendation::getId).toList();if(!ids.isEmpty())feedbackRepository.deleteByRecommendationIdIn(ids);recommendationRepository.deleteAll(values);}
 public record CreateRecommendation(String userId,String title,String rationale,Recommendation.Status status,UUID checkInId,RecoveryAttempt.Action action,Integer durationSeconds,Recommendation.Source source){
  public CreateRecommendation(String userId,String title,String rationale,Recommendation.Status status){this(userId,title,rationale,status,null,null,null,Recommendation.Source.RULE);}
  public CreateRecommendation(String userId,String title,String rationale,Recommendation.Status status,UUID checkInId){this(userId,title,rationale,status,checkInId,null,null,Recommendation.Source.RULE);}
 }
 public record CreateFeedback(boolean completed,boolean helpful,String note){}
 public static class RecommendationNotFoundException extends RuntimeException{public RecommendationNotFoundException(UUID id){super("Recommendation not found: "+id);}}
}
