package app.morrow.api;
import app.morrow.dashboard.DashboardService;
import app.morrow.recommendation.Recommendation;
import app.morrow.timeline.Timeline;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
@RestController @RequestMapping("/api/v1")
public class DashboardController {
 private final DashboardService service; public DashboardController(DashboardService service){this.service=service;}
 @GetMapping("/dashboard") DashboardResponse dashboard(@RequestParam(defaultValue="default-user")String userId){var data=service.getDashboard(userId);return new DashboardResponse(data.wellnessLoad(),data.score(),MetricsResponse.from(data.metrics()),data.timelines().stream().map(TimelineResponse::from).collect(Collectors.toList()),data.recommendation()!=null?RecommendationResponse.from(data.recommendation()):null,"의료 진단이 아닌 일상 웰니스 분석입니다.");}
 record MetricsResponse(int sleepMinutes,int restingHeartRate,int hrv,int steps){static MetricsResponse from(DashboardService.Metrics m){return new MetricsResponse(m.sleepMinutes(),m.restingHeartRate(),m.hrv(),m.steps());}}
 record TimelineResponse(String id,String time,String title,String detail,String kind,boolean userConfirmed){static TimelineResponse from(Timeline t){return new TimelineResponse(t.getId().toString(),t.getTime().format(DateTimeFormatter.ofPattern("HH:mm")),t.getTitle(),t.getDetail(),t.getKind().name(),t.isUserConfirmed());}}
 record RecommendationResponse(String id,String title,String rationale){static RecommendationResponse from(Recommendation r){return new RecommendationResponse(r.getId().toString(),r.getTitle(),r.getRationale());}}
 record DashboardResponse(String wellnessLoad,int score,MetricsResponse metrics,List<TimelineResponse> timeline,RecommendationResponse recommendation,String disclaimer){}
}
