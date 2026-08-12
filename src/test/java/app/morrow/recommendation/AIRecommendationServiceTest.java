package app.morrow.recommendation;

import app.morrow.checkin.CheckIn;
import app.morrow.recovery.RecoveryAttempt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIRecommendationServiceTest {
    @Test
    void parsesValidatedStructuredRecommendation() {
        var parsed = AIRecommendationService.parse(
                "WALK|420|7분 동안 가볍게 걸어보세요|짧은 수면과 이전 걷기 효과를 함께 반영했어요.",
                CheckIn.Status.TIRED
        );

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().action()).isEqualTo(RecoveryAttempt.Action.WALK);
        assertThat(parsed.orElseThrow().durationSeconds()).isEqualTo(420);
    }

    @Test
    void rejectsActiveRecommendationForUncomfortableCheckIn() {
        var parsed = AIRecommendationService.parse(
                "WALK|300|5분 걸어보세요|최근 움직임이 적었어요.",
                CheckIn.Status.UNCOMFORTABLE
        );

        assertThat(parsed).isEmpty();
    }

    @Test
    void rejectsExcessiveDuration() {
        var parsed = AIRecommendationService.parse(
                "BREATH|900|15분 호흡하세요|긴장을 낮춰보세요.",
                CheckIn.Status.TENSE
        );

        assertThat(parsed).isEmpty();
    }
}
