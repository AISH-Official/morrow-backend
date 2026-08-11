package app.morrow.dashboard;

import app.morrow.checkin.CheckIn;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class RecoveryScoreCalculator {
    public Assessment calculate(HealthSignalSnapshot current, List<HealthSignalSnapshot> history, List<CheckIn> todayCheckIns) {
        if (current == null || !hasSignal(current)) {
            if (todayCheckIns.isEmpty()) return new Assessment(0, "NO_DATA", false, "NONE", List.of("아직 동기화된 건강 데이터가 없습니다."));
            var directScore = todayCheckIns.get(0).getStatus() == CheckIn.Status.OK ? 74 : 62;
            return new Assessment(directScore, wellnessLoad(directScore), false, "CHECKIN_ONLY", List.of("건강 데이터 없이 최근 직접 체크인만 반영한 점수예요."));
        }

        var reasons = new ArrayList<String>();
        var baselineSignals = 0;
        var score = 82;

        var sleepBaseline = baseline(history, value -> number(value.getSleepMinutes()), 420);
        if (sleepBaseline.personalized()) baselineSignals++;
        if (positive(current.getSleepMinutes())) {
            var difference = sleepBaseline.value() - current.getSleepMinutes();
            if (difference > 0) {
                score -= Math.min(30, (int) Math.ceil(difference / 3.0));
                reasons.add("수면이 최근 기준보다 " + Math.max(1, (int) Math.round(difference)) + "분 짧아요.");
            } else {
                score += Math.min(5, (int) Math.abs(difference) / 30);
                reasons.add("수면이 최근 기준 범위예요.");
            }
        }

        var hrvBaseline = baseline(history, value -> value.getHrv(), 45);
        if (hrvBaseline.personalized()) baselineSignals++;
        if (positive(current.getHrv())) {
            var difference = current.getHrv() - hrvBaseline.value();
            if (difference < 0) {
                score -= Math.min(20, (int) Math.round(Math.abs(difference) * 0.6));
                reasons.add("HRV가 최근 기준보다 낮아요.");
            } else {
                score += Math.min(5, (int) Math.round(difference * 0.2));
                reasons.add("HRV가 최근 기준 범위예요.");
            }
        }

        var heartBaseline = baseline(history, value -> value.getRestingHeartRate(), 72);
        if (heartBaseline.personalized()) baselineSignals++;
        if (positive(current.getRestingHeartRate())) {
            var difference = current.getRestingHeartRate() - heartBaseline.value();
            if (difference > 0) {
                score -= Math.min(16, (int) Math.round(difference * 2));
                reasons.add("안정 심박이 최근 기준보다 높아요.");
            } else {
                score += Math.min(4, (int) Math.round(Math.abs(difference) * 0.3));
                reasons.add("안정 심박이 최근 기준 범위예요.");
            }
        }

        if (positive(current.getExerciseMinutes())) {
            if (current.getExerciseMinutes() >= 40) score += 8;
            else if (current.getExerciseMinutes() >= 20) score += 5;
        }
        if (!todayCheckIns.isEmpty()) {
            if (todayCheckIns.get(0).getStatus() == CheckIn.Status.OK) {
                score += 4;
                reasons.add("직접 남긴 상태가 괜찮음이에요.");
            } else {
                score -= 8;
                reasons.add("최근 직접 체크인 상태를 함께 반영했어요.");
            }
        }

        var confidence = baselineSignals >= 3 ? "HIGH" : baselineSignals >= 1 ? "MEDIUM" : "LOW";
        var normalized = clamp(score);
        return new Assessment(normalized, wellnessLoad(normalized), true, confidence, reasons.stream().limit(4).toList());
    }

    public int healthScore(HealthSignalSnapshot health) {
        return calculate(health, List.of(), List.of()).score();
    }

    public String wellnessLoad(int score) {
        if (score >= 70) return "NORMAL";
        if (score >= 45) return "MODERATE";
        return "HIGHER_THAN_USUAL";
    }

    private Baseline baseline(List<HealthSignalSnapshot> history, Function<HealthSignalSnapshot, Double> extractor, double fallback) {
        var values = history.stream().map(extractor).filter(this::positive).toList();
        if (values.size() < 3) return new Baseline(fallback, false);
        return new Baseline(values.stream().mapToDouble(Double::doubleValue).average().orElse(fallback), true);
    }

    private Double number(Integer value) { return value == null ? null : value.doubleValue(); }
    private boolean hasSignal(HealthSignalSnapshot value) {
        return positive(value.getSleepMinutes()) || positive(value.getHrv()) || positive(value.getRestingHeartRate())
                || positive(value.getSteps()) || positive(value.getExerciseMinutes()) || positive(value.getActiveEnergyKcal());
    }
    private boolean positive(Number value) { return value != null && value.doubleValue() > 0; }
    private int clamp(int score) { return Math.max(0, Math.min(100, score)); }

    private record Baseline(double value, boolean personalized) {}
    public record Assessment(int score, String wellnessLoad, boolean hasHealthData, String confidence, List<String> reasons) {}
}
