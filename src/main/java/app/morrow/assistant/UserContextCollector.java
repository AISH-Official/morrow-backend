package app.morrow.assistant;
import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineRepository;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
@Component
public class UserContextCollector {
 private final CheckInRepository checkInRepository; private final TimelineRepository timelineRepository; private final RecommendationRepository recommendationRepository; private final RecommendationFeedbackRepository feedbackRepository; private final AssistantMessageRepository messageRepository;
 public UserContextCollector(CheckInRepository checkInRepository,TimelineRepository timelineRepository,RecommendationRepository recommendationRepository,RecommendationFeedbackRepository feedbackRepository,AssistantMessageRepository messageRepository){this.checkInRepository=checkInRepository;this.timelineRepository=timelineRepository;this.recommendationRepository=recommendationRepository;this.feedbackRepository=feedbackRepository;this.messageRepository=messageRepository;}
 public UserContext collectContext(String userId){var weekAgo=OffsetDateTime.now().minusDays(7);var recentCheckIns=checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId,weekAgo);var recentTimelines=timelineRepository.findByUserIdAndCreatedAtAfterOrderByTimeAsc(userId,weekAgo);var recentRecommendations=recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId,weekAgo);var recentMessages=messageRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId,OffsetDateTime.now().minusHours(24));var feedbackSummary=recentRecommendations.stream().flatMap(r->feedbackRepository.findByRecommendationId(r.getId()).stream()).collect(Collectors.toList());return new UserContext(userId,recentCheckIns,recentTimelines,recentRecommendations,feedbackSummary,recentMessages);}
 public record UserContext(String userId,List<CheckIn> recentCheckIns,List<Timeline> recentTimelines,List<Recommendation> recentRecommendations,List<?> feedbackSummary,List<AssistantMessage> recentMessages){}
}
