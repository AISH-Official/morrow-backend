package app.morrow.assistant;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.personalization.PersonalizationService;
import app.morrow.personalization.UserMemory;
import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserContextCollector {
    private final HealthSignalSnapshotRepository healthSnapshotRepository;
    private final CheckInRepository checkInRepository;
    private final TimelineRepository timelineRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final AssistantMessageRepository messageRepository;
    private final PersonalizationService personalizationService;
    private final boolean includeHealthData;

    public UserContextCollector(
            HealthSignalSnapshotRepository healthSnapshotRepository,
            CheckInRepository checkInRepository,
            TimelineRepository timelineRepository,
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository,
            AssistantMessageRepository messageRepository,
            PersonalizationService personalizationService,
            @Value("${morrow.assistant.include-health-data:false}") boolean includeHealthData
    ) {
        this.healthSnapshotRepository = healthSnapshotRepository;
        this.checkInRepository = checkInRepository;
        this.timelineRepository = timelineRepository;
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
        this.messageRepository = messageRepository;
        this.personalizationService = personalizationService;
        this.includeHealthData = includeHealthData;
    }

    public UserContext collectContext(String userId) {
        var weekAgo = OffsetDateTime.now().minusDays(7);
        var recentHealthSnapshots = includeHealthData
                ? healthSnapshotRepository.findTop12ByUserIdOrderByRecordedAtDesc(userId)
                : List.<HealthSignalSnapshot>of();
        var recentCheckIns = checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId, weekAgo);
        var recentTimelines = timelineRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, weekAgo);
        var recentRecommendations = recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, weekAgo);
        var recentMessages = messageRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(
                userId,
                OffsetDateTime.now().minusHours(24)
        );
        var feedbackSummary = recentRecommendations.stream()
                .flatMap(recommendation -> feedbackRepository.findByRecommendationId(recommendation.getId()).stream())
                .collect(Collectors.toList());
        var memories = personalizationService.activeMemories(userId);

        return new UserContext(
                userId,
                recentHealthSnapshots,
                recentCheckIns,
                recentTimelines,
                recentRecommendations,
                feedbackSummary,
                recentMessages,
                memories
        );
    }

    public record UserContext(
            String userId,
            List<HealthSignalSnapshot> recentHealthSnapshots,
            List<CheckIn> recentCheckIns,
            List<Timeline> recentTimelines,
            List<Recommendation> recentRecommendations,
            List<?> feedbackSummary,
            List<AssistantMessage> recentMessages,
            List<UserMemory> memories
    ) {}
}
