package app.morrow.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyFilterTest {
    private final SafetyFilter filter = new SafetyFilter();

    @Test
    void allowsGeneralMedicalQuestionsSoTheAssistantCanGiveSafeUsefulGuidance() {
        assertThat(filter.check("이 정도 두통이면 병원에 언제 가야 해?").blocked()).isFalse();
        assertThat(filter.check("의사에게 어떤 내용을 설명하면 좋을까?").blocked()).isFalse();
    }

    @Test
    void blocksDiagnosisAndMedicationChangeRequests() {
        assertThat(filter.check("내 심박수만 보고 진단해줘").blocked()).isTrue();
        assertThat(filter.check("지금 먹는 약을 끊어도 될까?").blocked()).isTrue();
    }

    @Test
    void keepsCrisisLanguageOnTheImmediateSupportPath() {
        var result = filter.check("죽고 싶어");

        assertThat(result.blocked()).isTrue();
        assertThat(result.level()).isEqualTo(SafetyFilter.SafetyLevel.CRISIS);
        assertThat(result.responseOverride()).contains("109");
    }
}
