package app.morrow.dashboard;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationService;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineService;
import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
@Service @Transactional(readOnly=true)
public class DashboardService {
 private final CheckInRepository checkInRepository; private final TimelineService timelineService; private final RecommendationService recommendationService; private final HealthSignalSnapshotService healthSnapshots; private final RecoveryScoreCalculator recoveryScores; private final ZoneId timeZone;
 public DashboardService(CheckInRepository checkInRepository,TimelineService timelineService,RecommendationService recommendationService,HealthSignalSnapshotService healthSnapshots,RecoveryScoreCalculator recoveryScores,@Value("${morrow.time-zone:Asia/Seoul}")String timeZone){this.checkInRepository=checkInRepository;this.timelineService=timelineService;this.recommendationService=recommendationService;this.healthSnapshots=healthSnapshots;this.recoveryScores=recoveryScores;this.timeZone=ZoneId.of(timeZone);}
 public DashboardData getDashboard(String userId){var todayStart=OffsetDateTime.now(timeZone).toLocalDate().atStartOfDay(timeZone).toOffsetDateTime();var recentCheckIns=checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId,todayStart);var snapshot=scoreSnapshot(userId);var history=healthSnapshots.recent(userId).stream().filter(value->snapshot==null||!value.getId().equals(snapshot.getId())).toList();var assessment=recoveryScores.calculate(snapshot,history,recentCheckIns);var metrics=calculateMetrics(userId);var timelines=timelineService.findRecentByUserId(userId,todayStart).stream().sorted(Comparator.comparing((Timeline value)->value.getDisplayTime(timeZone)).thenComparing(Timeline::getCreatedAt)).toList();var activeRecommendation=recommendationService.findActiveByUserId(userId).orElse(null);return new DashboardData(assessment.wellnessLoad(),assessment.score(),assessment.hasHealthData(),assessment.confidence(),assessment.reasons(),snapshot==null?null:snapshot.getRecordedAt(),metrics,timelines,activeRecommendation);}
 private HealthSignalSnapshot scoreSnapshot(String userId){return healthSnapshots.latest(userId,HealthSignalSnapshot.Source.IPHONE).orElseGet(()->healthSnapshots.latest(userId).orElse(null));}
 private Metrics calculateMetrics(String userId){
  var latest=healthSnapshots.latest(userId).orElse(null);var phone=healthSnapshots.latest(userId,HealthSignalSnapshot.Source.IPHONE).orElse(null);
  if(latest==null&&phone==null)return new Metrics(0,0,0,0,0,0);
  return new Metrics(firstPositiveInt(latest==null?null:latest.getSleepMinutes(),phone==null?null:phone.getSleepMinutes()),firstPositive(latest==null?null:latest.getRestingHeartRate(),phone==null?null:phone.getRestingHeartRate()),firstPositive(latest==null?null:latest.getHrv(),phone==null?null:phone.getHrv()),firstPositive(latest==null?null:latest.getSteps(),phone==null?null:phone.getSteps()),firstPositive(latest==null?null:latest.getActiveEnergyKcal(),phone==null?null:phone.getActiveEnergyKcal()),firstPositive(latest==null?null:latest.getExerciseMinutes(),phone==null?null:phone.getExerciseMinutes()));
 }
 private int firstPositive(Double primary,Double fallback){return rounded(primary!=null&&primary>0?primary:fallback);}
 private int firstPositiveInt(Integer primary,Integer fallback){return primary!=null&&primary>0?primary:(fallback==null?0:fallback);}
 private int rounded(Double value){return value==null?0:(int)Math.round(value);}
 public record DashboardData(String wellnessLoad,int score,boolean hasHealthData,String scoreConfidence,List<String> scoreReasons,OffsetDateTime lastUpdatedAt,Metrics metrics,List<Timeline> timelines,Recommendation recommendation){}
 public record Metrics(int sleepMinutes,int restingHeartRate,int hrv,int steps,int activeEnergyKcal,int exerciseMinutes){}
}
