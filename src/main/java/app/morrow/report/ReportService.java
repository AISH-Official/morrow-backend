package app.morrow.report;
import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service @Transactional(readOnly=true)
public class ReportService {
 private final CheckInRepository checkInRepository; public ReportService(CheckInRepository checkInRepository){this.checkInRepository=checkInRepository;}
 public WeeklyReport generateWeeklyReport(String userId){var weekAgo=OffsetDateTime.now().minusDays(7);var checkIns=checkInRepository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(userId,weekAgo);var totalCount=checkIns.size();var statusCounts=checkInRepository.countByStatusAfter(userId,weekAgo).stream().collect(Collectors.toMap(arr->(CheckIn.Status)arr[0],arr->(Long)arr[1]));var causeCounts=checkInRepository.countByCauseAfter(userId,weekAgo).stream().collect(Collectors.toMap(arr->(CheckIn.Cause)arr[0],arr->(Long)arr[1]));var topStatus=statusCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().name()).orElse(null);var topCause=causeCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().name()).orElse(null);var okCount=statusCounts.getOrDefault(CheckIn.Status.OK,0L);var improvement=totalCount>0?(double)okCount/totalCount*100:0.0;var patterns=generatePatterns(topStatus,topCause,improvement);var insights=generateInsights(totalCount,topStatus,topCause,improvement);return new WeeklyReport(totalCount,topStatus,topCause,improvement,patterns,insights);}
 private List<String> generatePatterns(String topStatus,String topCause,double improvement){if(topStatus==null)return List.of();return List.of(topStatus+" 상태가 가장 많이 기록됨",(topCause!=null?topCause+" 원인이 주요 요인":"다양한 원인이 있음"),"개선율: "+String.format("%.1f",improvement)+"%");}
 private String generateInsights(int totalCount,String topStatus,String topCause,double improvement){if(totalCount==0)return "이번 주 체크인 기록이 없습니다.";if(improvement>=60)return "이번 주는 전반적으로 안정적인 상태를 유지하셨습니다.";if(improvement>=40)return "일부 어려움이 있었지만 회복 중입니다.";return "이번 주는 어려운 한 주였습니다. 충분한 휴식과 지원이 필요합니다.";}
 public record WeeklyReport(int totalCheckIns,String topStatus,String topCause,double improvementRate,List<String> patterns,String insights){}
}
