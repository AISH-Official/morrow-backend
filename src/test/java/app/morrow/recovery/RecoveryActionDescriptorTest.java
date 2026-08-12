package app.morrow.recovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryActionDescriptorTest {
    @Test
    void recognizesConjugatedKoreanWalkingRecommendation() {
        var descriptor = RecoveryActionDescriptor.fromTitle("7분 동안 가볍게 걸어보세요");

        assertThat(descriptor.action()).isEqualTo(RecoveryAttempt.Action.WALK);
        assertThat(descriptor.durationSeconds()).isEqualTo(420);
    }
}
