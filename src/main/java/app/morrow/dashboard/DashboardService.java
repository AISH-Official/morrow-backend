package app.morrow.dashboard;
import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationService;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
@Service @Transactional(readOnly=true)
public class DashboardService {
 private final CheckInRepository checkInRepository; private final TimelineService timelineService; private final RecommendationService recommendationService;
 public DashboardService(CheckInRepository checkInRepository,TimelineService timelineService,RecommendationService recommendationService){this.checkInRepository=checkInRepository;this.timelineService=timelineService;this.recommendationService=recommendationService;}
 public DashboardData getDashboard(String userId){var todayStart=OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);var weekAgo=OffsetDateTime.now().minusDays(7);var recentCheckIns=checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId,todayStart);var weekCheckIns=checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId,weekAgo);var wellnessLoad=calculateWellnessLoad(recentCheckIns);var score=calculateScore(recentCheckIns,weekCheckIns);var metrics=calculateMetrics(weekCheckIns);var timelines=timelineService.findRecentByUserId(userId,todayStart);var activeRecommendation=recommendationService.findActiveByUserId(userId).orElse(null);return new DashboardData(wellnessLoad,score,metrics,timelines,activeRecommendation);}
 private String calculateWellnessLoad(List<CheckIn> checkIns){if(checkIns.isEmpty())return "NORMAL";var notOkCount=checkIns.stream().filter(c->c.getStatus()!=CheckIn.Status.OK).count();if(notOkCount>=checkIns.size()*0.7)return "HIGHER_THAN_USUAL";if(notOkCount>=checkIns.size()*0.3)return "MODERATE";return "NORMAL";}
 private int calculateScore(List<CheckIn> todayCheckIns,List<CheckIn> weekCheckIns){if(weekCheckIns.isEmpty())return 70;var okCount=weekCheckIns.stream().filter(c->c.getStatus()==CheckIn.Status.OK).count();var baseScore=(int)((double)okCount/weekCheckIns.size()*100);var todayPenalty=todayCheckIns.stream().filter(c->c.getStatus()!=CheckIn.Status.OK).count()*5;return Math.max(0,Math.min(100,baseScore-(int)todayPenalty));}
 private Metrics calculateMetrics(List<CheckIn> weekCheckIns){return new Metrics(420,68,41,6543);}
 public record DashboardData(String wellnessLoad,int score,Metrics metrics,List<Timeline> timelines,Recommendation recommendation){}
 public record Metrics(int sleepMinutes,int restingHeartRate,int hrv,int steps){}
}
