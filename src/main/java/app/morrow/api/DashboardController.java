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
 @GetMapping("/dashboard") DashboardResponse dashboard(@RequestParam(defaultValue="default-user")String userId){var data=service.getDashboard(users.resolve(userId));return new DashboardResponse(data.wellnessLoad(),data.score(),data.hasHealthData(),data.scoreConfidence(),data.scoreReasons(),data.lastUpdatedAt(),MetricsResponse.from(data.metrics()),HealthDetailsResponse.from(data.healthDetails()),data.timelines().stream().map(value->TimelineResponse.from(value,timeZone)).collect(Collectors.toList()),data.recommendation()!=null?RecommendationResponse.from(data.recommendation()):null,"의료 진단이 아닌 일상 웰니스 분석입니다.");}
 record MetricsResponse(int sleepMinutes,int restingHeartRate,int hrv,int steps,int activeEnergyKcal,int exerciseMinutes){static MetricsResponse from(DashboardService.Metrics m){return new MetricsResponse(m.sleepMinutes(),m.restingHeartRate(),m.hrv(),m.steps(),m.activeEnergyKcal(),m.exerciseMinutes());}}
 record HealthDetailsResponse(SleepResponse sleep,List<WorkoutResponse> workouts){static HealthDetailsResponse from(app.morrow.health.HealthSignalSnapshotService.HealthDetails value){return new HealthDetailsResponse(value.sleep()==null?null:SleepResponse.from(value.sleep()),value.workouts().stream().map(WorkoutResponse::from).toList());}}
 record SleepResponse(String clientSleepId,OffsetDateTime startAt,OffsetDateTime endAt,int totalMinutes,int coreMinutes,int deepMinutes,int remMinutes,int awakeMinutes,String source){static SleepResponse from(app.morrow.health.HealthSignalSnapshotService.SleepSession value){return new SleepResponse(value.clientSleepId(),value.startAt(),value.endAt(),value.totalMinutes(),value.coreMinutes(),value.deepMinutes(),value.remMinutes(),value.awakeMinutes(),value.source());}}
 record WorkoutResponse(String clientWorkoutId,String activityType,OffsetDateTime startAt,OffsetDateTime endAt,double durationMinutes,double activeEnergyKcal,double distanceMeters,double averageHeartRate,double maxHeartRate,String intensity,String source){static WorkoutResponse from(app.morrow.health.HealthSignalSnapshotService.WorkoutSession value){return new WorkoutResponse(value.clientWorkoutId(),value.activityType(),value.startAt(),value.endAt(),value.durationMinutes(),value.activeEnergyKcal(),value.distanceMeters(),value.averageHeartRate(),value.maxHeartRate(),value.intensity(),value.source());}}
 record TimelineResponse(String id,String time,String title,String detail,String kind,boolean userConfirmed){static TimelineResponse from(Timeline t,ZoneId zone){return new TimelineResponse(t.getId().toString(),t.getDisplayTime(zone).format(DateTimeFormatter.ofPattern("HH:mm")),t.getTitle(),t.getDetail(),t.getKind().name(),t.isUserConfirmed());}}
 record RecommendationResponse(String id,String title,String rationale){static RecommendationResponse from(Recommendation r){return new RecommendationResponse(r.getId().toString(),r.getTitle(),r.getRationale());}}
 record DashboardResponse(String wellnessLoad,int score,boolean hasHealthData,String scoreConfidence,List<String> scoreReasons,OffsetDateTime lastUpdatedAt,MetricsResponse metrics,HealthDetailsResponse healthDetails,List<TimelineResponse> timeline,RecommendationResponse recommendation,String disclaimer){}
}
