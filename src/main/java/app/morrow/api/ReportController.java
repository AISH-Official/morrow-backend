package app.morrow.api;
import app.morrow.report.ReportService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/reports")
public class ReportController {
 private final ReportService service; public ReportController(ReportService service){this.service=service;}
 @GetMapping("/weekly") WeeklyReportResponse getWeeklyReport(){var report=service.generateWeeklyReport();return WeeklyReportResponse.from(report);}
 record WeeklyReportResponse(int totalCheckIns,String topStatus,String topCause,double improvementRate,List<String> patterns,String insights){static WeeklyReportResponse from(ReportService.WeeklyReport r){return new WeeklyReportResponse(r.totalCheckIns(),r.topStatus(),r.topCause(),r.improvementRate(),r.patterns(),r.insights());}}
}
