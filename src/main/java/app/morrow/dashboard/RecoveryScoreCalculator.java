package app.morrow.dashboard;

import app.morrow.checkin.CheckIn;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RecoveryScoreCalculator {
    public int calculate(HealthSignalSnapshot health, List<CheckIn> todayCheckIns) {
        var score = health == null ? 70 : healthScore(health);
        if (!todayCheckIns.isEmpty()) score += todayCheckIns.get(0).getStatus() == CheckIn.Status.OK ? 4 : -8;
        return clamp(score);
    }

    public int healthScore(HealthSignalSnapshot health) {
        var score = 82;
        var hasSignal = false;
        if (positive(health.getSleepMinutes())) {
            hasSignal = true;
            var difference = 420 - health.getSleepMinutes();
            if (difference > 0) score -= Math.min(30, (int)Math.ceil(difference / 3.0));
            else score += Math.min(5, Math.abs(difference) / 30);
        }
        if (positive(health.getHrv())) {
            hasSignal = true;
            if (health.getHrv() < 45) score -= Math.min(20, (int)Math.round((45 - health.getHrv()) * 0.6));
            else score += Math.min(5, (int)Math.round((health.getHrv() - 45) * 0.2));
        }
        if (positive(health.getRestingHeartRate())) {
            hasSignal = true;
            if (health.getRestingHeartRate() > 72) score -= Math.min(16, (int)Math.round((health.getRestingHeartRate() - 72) * 2));
            else score += Math.min(4, (int)Math.round((72 - health.getRestingHeartRate()) * 0.3));
        }
        if (positive(health.getExerciseMinutes())) {
            hasSignal = true;
            if (health.getExerciseMinutes() >= 40) score += 8;
            else if (health.getExerciseMinutes() >= 20) score += 5;
        }
        return hasSignal ? clamp(score) : 70;
    }

    public String wellnessLoad(int score) {
        if (score >= 70) return "NORMAL";
        if (score >= 45) return "MODERATE";
        return "HIGHER_THAN_USUAL";
    }

    private boolean positive(Number value) { return value != null && value.doubleValue() > 0; }
    private int clamp(int score) { return Math.max(0, Math.min(100, score)); }
}
