package app.morrow.privacy;

import app.morrow.assistant.AssistantMessageRepository;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import app.morrow.timeline.TimelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class DataPrivacyService {
 private final CheckInRepository checkIns;
 private final TimelineRepository timelines;
 private final RecommendationRepository recommendations;
 private final RecommendationFeedbackRepository feedback;
 private final AssistantMessageRepository messages;

 public DataPrivacyService(CheckInRepository checkIns,TimelineRepository timelines,RecommendationRepository recommendations,RecommendationFeedbackRepository feedback,AssistantMessageRepository messages){
  this.checkIns=checkIns;this.timelines=timelines;this.recommendations=recommendations;this.feedback=feedback;this.messages=messages;
 }

 public void deleteAllForUser(String userId){
  var recommendationIds=recommendations.findByUserId(userId).stream().map(value->value.getId()).toList();
  if(!recommendationIds.isEmpty())feedback.deleteByRecommendationIdIn(recommendationIds);
  recommendations.deleteByUserId(userId);
  timelines.deleteByUserId(userId);
  messages.deleteByUserId(userId);
  checkIns.deleteByUserId(userId);
 }
}
