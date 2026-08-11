package app.morrow.privacy;

import app.morrow.assistant.AssistantMessageRepository;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import app.morrow.timeline.TimelineRepository;
import app.morrow.personalization.UserMemoryRepository;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.notification.PushDeviceRepository;
import app.morrow.recovery.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class DataPrivacyService {
 private final CheckInRepository checkIns;
 private final TimelineRepository timelines;
 private final RecommendationRepository recommendations;
 private final RecommendationFeedbackRepository feedback;
 private final AssistantMessageRepository messages;
 private final UserMemoryRepository memories;
 private final HealthSignalSnapshotRepository healthSnapshots;
 private final PushDeviceRepository pushDevices;
 private final RecoveryAttemptRepository recoveryAttempts;

 public DataPrivacyService(CheckInRepository checkIns,TimelineRepository timelines,RecommendationRepository recommendations,RecommendationFeedbackRepository feedback,AssistantMessageRepository messages,UserMemoryRepository memories,HealthSignalSnapshotRepository healthSnapshots,PushDeviceRepository pushDevices,RecoveryAttemptRepository recoveryAttempts){
  this.checkIns=checkIns;this.timelines=timelines;this.recommendations=recommendations;this.feedback=feedback;this.messages=messages;this.memories=memories;this.healthSnapshots=healthSnapshots;this.pushDevices=pushDevices;this.recoveryAttempts=recoveryAttempts;
 }

 public void deleteAllForUser(String userId){
  resetWellnessForDemo(userId);
  pushDevices.deleteByUserId(userId);
 }

 public void resetWellnessForDemo(String userId){
  var recommendationIds=recommendations.findByUserId(userId).stream().map(value->value.getId()).toList();
  if(!recommendationIds.isEmpty())feedback.deleteByRecommendationIdIn(recommendationIds);
  recommendations.deleteByUserId(userId);
  timelines.deleteByUserId(userId);
  messages.deleteByUserId(userId);
  checkIns.deleteByUserId(userId);
  memories.deleteByUserId(userId);
  healthSnapshots.deleteByUserId(userId);
  recoveryAttempts.deleteByUserId(userId);
 }
}
