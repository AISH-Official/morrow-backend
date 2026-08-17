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
}
