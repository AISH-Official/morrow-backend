package app.morrow.report;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.RecommendationFeedback;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptRepository;
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
    private final RecoveryAttemptRepository recoveryAttempts;
    private final RecommendationFeedbackRepository recommendationFeedbacks;
    private final ZoneId timeZone;

    public ReportService(CheckInRepository checkIns, RecoveryAttemptRepository recoveryAttempts, RecommendationFeedbackRepository recommendationFeedbacks, @Value("${morrow.time-zone:Asia/Seoul}") String timeZone) {
        this.checkIns = checkIns;
        this.recoveryAttempts = recoveryAttempts;
        this.recommendationFeedbacks = recommendationFeedbacks;
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
        var attempts = recoveryAttempts.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, previousStart.atStartOfDay(timeZone).toOffsetDateTime());
        var currentAttempts = attempts.stream().filter(value -> !localDate(value).isBefore(currentStart)).toList();
        var completedAttempts = attempts.stream().filter(value -> value.getStatus() == RecoveryAttempt.Status.COMPLETED && value.getOutcome() != null && value.getCompletedAt() != null && !localDate(value.getCompletedAt()).isBefore(currentStart)).toList();
        var feedbacks = recommendationFeedbacks.findForUserAfter(userId, previousStart.atStartOfDay(timeZone).toOffsetDateTime());
        var completedFeedbacks = feedbacks.stream().filter(value -> value.isCompleted() && !localDate(value).isBefore(currentStart)).toList();
        var recoveryStats = combineRecoveryStats(currentAttempts, completedAttempts, completedFeedbacks);

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
        var improvedCounts = completedAttempts.stream().filter(value -> value.getOutcome() == RecoveryAttempt.Outcome.IMPROVED).collect(Collectors.groupingBy(RecoveryAttempt::getAction, Collectors.counting()));
        var topHelpfulAction = improvedCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(value -> actionLabel(value.getKey())).orElse(null);
        var helpfulRate = recoveryStats.completed() == 0 ? 0 : recoveryStats.improved() * 100.0 / recoveryStats.completed();
        var patterns = new ArrayList<String>();
        if (!current.isEmpty()) {
            patterns.add(topStatus + " 상태가 가장 많이 기록됨");
            patterns.add(topCause == null ? "다양한 원인이 기록됨" : topCause + " 원인이 주요 맥락");
            patterns.add(previous.isEmpty() ? "비교할 지난주 기록이 더 필요함" : String.format("지난주보다 회복 흐름 %+.1f점", change));
        }
        if (recoveryStats.completed() > 0) patterns.add("회복 행동 " + recoveryStats.completed() + "회 실행 · " + Math.round(helpfulRate) + "%에서 나아짐");
        var insight = insight(current.size(), average, change, previous.isEmpty());
        var recoveryInsight = recoveryInsight(recoveryStats.suggested(), recoveryStats.completed(), topHelpfulAction, helpfulRate);
        return new WeeklyReport(current.size(), topStatus, topCause, average, change, daily, patterns, insight,
                recoveryStats.suggested(), recoveryStats.completed(), helpfulRate, topHelpfulAction, recoveryInsight);
    }

    private LocalDate localDate(CheckIn value) { return value.getRecordedAt().atZoneSameInstant(timeZone).toLocalDate(); }
    private LocalDate localDate(RecoveryAttempt value) { return value.getCreatedAt().atZoneSameInstant(timeZone).toLocalDate(); }
    private LocalDate localDate(RecommendationFeedback value) { return localDate(value.getCreatedAt()); }
    private LocalDate localDate(OffsetDateTime value) { return value.atZoneSameInstant(timeZone).toLocalDate(); }
    private RecoveryStats combineRecoveryStats(List<RecoveryAttempt> currentAttempts,List<RecoveryAttempt> completedAttempts,List<RecommendationFeedback> completedFeedbacks) {
        var unmatchedFeedbacks = new ArrayList<>(completedFeedbacks);
        for (var attempt : completedAttempts) {
            var completedAt = attempt.getCompletedAt();
            var closestIndex = -1;
            var closestSeconds = Long.MAX_VALUE;
            for (var index = 0; index < unmatchedFeedbacks.size(); index++) {
                var seconds = Math.abs(java.time.Duration.between(completedAt, unmatchedFeedbacks.get(index).getCreatedAt()).getSeconds());
                if (seconds <= 300 && seconds < closestSeconds) { closestIndex = index; closestSeconds = seconds; }
            }
            if (closestIndex >= 0) unmatchedFeedbacks.remove(closestIndex);
        }
        var improvedAttempts = completedAttempts.stream().filter(value -> value.getOutcome() == RecoveryAttempt.Outcome.IMPROVED).count();
        var improvedLegacyFeedbacks = unmatchedFeedbacks.stream().filter(RecommendationFeedback::isHelpful).count();
        var completed = completedAttempts.size() + unmatchedFeedbacks.size();
        return new RecoveryStats(Math.max(currentAttempts.size() + unmatchedFeedbacks.size(), completed), completed, improvedAttempts + improvedLegacyFeedbacks);
    }
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
    private String recoveryInsight(int suggested, int completed, String topAction, double helpfulRate) {
        if (suggested == 0) return "이번 주에는 아직 회복 행동 제안이 없었어요.";
        if (completed == 0) return "제안은 있었지만 완료 효과가 기록되지 않았어요. 다음 행동 뒤 한 번만 평가해 주세요.";
        if (topAction != null && helpfulRate >= 60) return topAction + "이 가장 잘 맞았어요. 다음 주에도 우선 제안할게요.";
        return "효과 피드백이 쌓이는 중이에요. 다음 주에는 더 잘 맞는 행동을 우선할게요.";
    }
    private String actionLabel(RecoveryAttempt.Action action) { return switch (action) {
        case BREATH -> "1분 호흡"; case WALK -> "짧은 걷기"; case WATER_WALK -> "물 한 잔과 걷기";
        case STRETCH -> "스트레칭"; case FOCUS -> "짧은 집중"; case SCREEN_BREAK -> "화면 휴식";
    }; }

    public record DailyPoint(LocalDate date, Integer score, int checkInCount) {}
    private record RecoveryStats(int suggested,int completed,long improved) {}
    public record WeeklyReport(int totalCheckIns, String topStatus, String topCause, double improvementRate,
                               double changeFromPrevious, List<DailyPoint> dailyScores, List<String> patterns, String insights,
                               int suggestedRecoveryCount, int completedRecoveryCount, double recoveryHelpfulRate,
                               String topHelpfulAction, String recoveryInsight) {}
}
