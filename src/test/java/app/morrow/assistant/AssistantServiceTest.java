package app.morrow.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantServiceTest {
    @Test
    void recognizesSkipDirectiveVariants() {
        assertThat(AssistantService.isSkipDirective("SKIP")).isTrue();
        assertThat(AssistantService.isSkipDirective("skip")).isTrue();
        assertThat(AssistantService.isSkipDirective("SKIP.")).isTrue();
        assertThat(AssistantService.isSkipDirective(" SKIP \n")).isTrue();
        assertThat(AssistantService.isSkipDirective("SKIP!")).isTrue();
    }

    @Test
    void keepsRealNotificationBodies() {
        assertThat(AssistantService.isSkipDirective("지금 물 한 잔 마시고 잠깐 걸어볼까요?")).isFalse();
        assertThat(AssistantService.isSkipDirective("SKIP하지 말고 지금 1분 호흡해요")).isFalse();
        assertThat(AssistantService.isSkipDirective("")).isFalse();
    }

    @Test
    void describesLastAlertRecencyForTheAiJudge() {
        assertThat(AssistantService.lastAlertNote(null)).isEqualTo("참고: 최근 발송한 회복 알림이 없습니다.");
        assertThat(AssistantService.lastAlertNote(java.time.OffsetDateTime.now().minusMinutes(40)))
                .isEqualTo("참고: 마지막 회복 알림을 약 40분 전에 보냈습니다.");
        assertThat(AssistantService.lastAlertNote(java.time.OffsetDateTime.now().minusHours(5)))
                .isEqualTo("참고: 마지막 회복 알림을 약 5시간 전에 보냈습니다.");
        assertThat(AssistantService.lastAlertNote(java.time.OffsetDateTime.now().minusDays(3)))
                .isEqualTo("참고: 마지막 회복 알림을 보낸 지 이틀 이상 지났습니다.");
    }
}
