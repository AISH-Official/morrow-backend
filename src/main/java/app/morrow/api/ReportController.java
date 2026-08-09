package app.morrow.api;
import app.morrow.report.ReportService;
import app.morrow.auth.RequestUserResolver;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/reports")
public class ReportController {
 private final ReportService service; private final RequestUserResolver users; public ReportController(ReportService service,RequestUserResolver users){this.service=service;this.users=users;}
 @GetMapping("/weekly") WeeklyReportResponse getWeeklyReport(@RequestParam(defaultValue="default-user")String userId){var report=service.generateWeeklyReport(users.resolve(userId));return WeeklyReportResponse.from(report);}
 record WeeklyReportResponse(int totalCheckIns,String topStatus,String topCause,double improvementRate,List<String> patterns,String insights){static WeeklyReportResponse from(ReportService.WeeklyReport r){return new WeeklyReportResponse(r.totalCheckIns(),r.topStatus(),r.topCause(),r.improvementRate(),r.patterns(),r.insights());}}
}
