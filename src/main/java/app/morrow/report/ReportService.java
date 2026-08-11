package app.morrow.report;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final CheckInRepository checkIns;
    private final ZoneId timeZone;

    public ReportService(CheckInRepository checkIns, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone) {
        this.checkIns = checkIns;
        this.timeZone = ZoneId.of(timeZone);
    }

    public WeeklyReport generateWeeklyReport(String userId) {
        var today = LocalDate.now(timeZone);
        var currentStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        var previousStart = currentStart.minusDays(7);
        var records = checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(
                userId, previousStart.atStartOfDay(timeZone).toOffsetDateTime());
        var current = records.stream().filter(value -> !localDate(value).isBefore(currentStart)).toList();
        var previous = records.stream().filter(value -> localDate(value).isBefore(currentStart)).toList();

        var statusCounts = current.stream().collect(Collectors.groupingBy(CheckIn::getStatus, Collectors.counting()));
        var causeCounts = current.stream().filter(value -> value.getCause() != null)
                .collect(Collectors.groupingBy(CheckIn::getCause, Collectors.counting()));
        var topStatus = maxName(statusCounts);
        var topCause = maxName(causeCounts);
        var average = averageScore(current);
        var previousAverage = averageScore(previous);
        var change = previous.isEmpty() ? 0 : average - previousAverage;
        var daily = new ArrayList<DailyPoint>();
        var currentEnd = currentStart.plusDays(6);
        for (var day = currentStart; !day.isAfter(currentEnd); day = day.plusDays(1)) {
            var target = day;
            var values = current.stream().filter(value -> localDate(value).equals(target)).toList();
            daily.add(new DailyPoint(day, values.isEmpty() ? null : (int) Math.round(averageScore(values)), values.size()));
        }
        var patterns = current.isEmpty() ? List.<String>of() : List.of(
                topStatus + " 상태가 가장 많이 기록됨",
                topCause == null ? "다양한 원인이 기록됨" : topCause + " 원인이 주요 맥락",
                previous.isEmpty() ? "비교할 지난주 기록이 더 필요함" : String.format("지난주보다 회복 흐름 %+.1f점", change));
        var insight = insight(current.size(), average, change, previous.isEmpty());
        return new WeeklyReport(current.size(), topStatus, topCause, average, change, daily, patterns, insight);
    }

    private LocalDate localDate(CheckIn value) { return value.getRecordedAt().atZoneSameInstant(timeZone).toLocalDate(); }
    private double averageScore(List<CheckIn> values) { return values.stream().mapToInt(value -> statusScore(value.getStatus())).average().orElse(0); }
    private int statusScore(CheckIn.Status status) { return switch (status) { case OK -> 100; case TENSE -> 60; case LOW_FOCUS -> 55; case TIRED -> 45; case UNCOMFORTABLE -> 35; }; }
    private <T extends Enum<T>> String maxName(Map<T, Long> values) { return values.entrySet().stream().max(Map.Entry.comparingByValue()).map(value -> value.getKey().name()).orElse(null); }
    private String insight(int count, double average, double change, boolean noComparison) {
        if (count == 0) return "이번 주 체크인 기록이 없습니다.";
        if (!noComparison && change >= 8) return "지난주보다 회복 흐름이 뚜렷하게 좋아졌어요.";
        if (!noComparison && change <= -8) return "지난주보다 부담 신호가 늘었어요. 회복 시간을 먼저 확보해 보세요.";
        if (average >= 70) return "이번 주는 전반적으로 안정적인 흐름을 유지했어요.";
        return "부담 신호가 반복됐어요. 가장 잦은 원인부터 작게 조정해 보세요.";
    }

    public record DailyPoint(LocalDate date, Integer score, int checkInCount) {}
    public record WeeklyReport(int totalCheckIns, String topStatus, String topCause, double improvementRate,
                               double changeFromPrevious, List<DailyPoint> dailyScores, List<String> patterns, String insights) {}
}
