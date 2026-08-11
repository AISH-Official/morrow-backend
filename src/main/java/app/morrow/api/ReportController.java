package app.morrow.api;
import app.morrow.report.ReportService;
import app.morrow.auth.RequestUserResolver;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;
@RestController @RequestMapping("/api/v1/reports")
public class ReportController {
 private final ReportService service; private final RequestUserResolver users; public ReportController(ReportService service,RequestUserResolver users){this.service=service;this.users=users;}
 @GetMapping("/weekly") WeeklyReportResponse getWeeklyReport(@RequestParam(defaultValue="default-user")String userId){var report=service.generateWeeklyReport(users.resolve(userId));return WeeklyReportResponse.from(report);}
 record DailyPointResponse(LocalDate date,Integer score,int checkInCount){static DailyPointResponse from(ReportService.DailyPoint value){return new DailyPointResponse(value.date(),value.score(),value.checkInCount());}}
 record WeeklyReportResponse(int totalCheckIns,String topStatus,String topCause,double improvementRate,double changeFromPrevious,List<DailyPointResponse> dailyScores,List<String> patterns,String insights,int suggestedRecoveryCount,int completedRecoveryCount,double recoveryHelpfulRate,String topHelpfulAction,String recoveryInsight){static WeeklyReportResponse from(ReportService.WeeklyReport r){return new WeeklyReportResponse(r.totalCheckIns(),r.topStatus(),r.topCause(),r.improvementRate(),r.changeFromPrevious(),r.dailyScores().stream().map(DailyPointResponse::from).toList(),r.patterns(),r.insights(),r.suggestedRecoveryCount(),r.completedRecoveryCount(),r.recoveryHelpfulRate(),r.topHelpfulAction(),r.recoveryInsight());}}
}
