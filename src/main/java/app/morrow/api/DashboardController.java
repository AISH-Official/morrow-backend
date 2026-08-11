package app.morrow.api;
import app.morrow.dashboard.DashboardService;
import app.morrow.auth.RequestUserResolver;
import app.morrow.recommendation.Recommendation;
import app.morrow.timeline.Timeline;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.OffsetDateTime;
@RestController @RequestMapping("/api/v1")
public class DashboardController {
 private final DashboardService service; private final RequestUserResolver users; private final ZoneId timeZone; public DashboardController(DashboardService service,RequestUserResolver users,@Value("${morrow.time-zone:Asia/Seoul}")String timeZone){this.service=service;this.users=users;this.timeZone=ZoneId.of(timeZone);}
 @GetMapping("/dashboard") DashboardResponse dashboard(@RequestParam(defaultValue="default-user")String userId){var data=service.getDashboard(users.resolve(userId));return new DashboardResponse(data.wellnessLoad(),data.score(),data.hasHealthData(),data.scoreConfidence(),data.scoreReasons(),data.lastUpdatedAt(),MetricsResponse.from(data.metrics()),data.timelines().stream().map(value->TimelineResponse.from(value,timeZone)).collect(Collectors.toList()),data.recommendation()!=null?RecommendationResponse.from(data.recommendation()):null,"의료 진단이 아닌 일상 웰니스 분석입니다.");}
 record MetricsResponse(int sleepMinutes,int restingHeartRate,int hrv,int steps,int activeEnergyKcal,int exerciseMinutes){static MetricsResponse from(DashboardService.Metrics m){return new MetricsResponse(m.sleepMinutes(),m.restingHeartRate(),m.hrv(),m.steps(),m.activeEnergyKcal(),m.exerciseMinutes());}}
 record TimelineResponse(String id,String time,String title,String detail,String kind,boolean userConfirmed){static TimelineResponse from(Timeline t,ZoneId zone){return new TimelineResponse(t.getId().toString(),t.getDisplayTime(zone).format(DateTimeFormatter.ofPattern("HH:mm")),t.getTitle(),t.getDetail(),t.getKind().name(),t.isUserConfirmed());}}
 record RecommendationResponse(String id,String title,String rationale){static RecommendationResponse from(Recommendation r){return new RecommendationResponse(r.getId().toString(),r.getTitle(),r.getRationale());}}
 record DashboardResponse(String wellnessLoad,int score,boolean hasHealthData,String scoreConfidence,List<String> scoreReasons,OffsetDateTime lastUpdatedAt,MetricsResponse metrics,List<TimelineResponse> timeline,RecommendationResponse recommendation,String disclaimer){}
}
