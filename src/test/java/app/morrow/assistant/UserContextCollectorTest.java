package app.morrow.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextCollectorTest {
    @Test
    void excludesGeneratedFallbackRepliesFromFutureAiContext() {
        var fallback = new AssistantMessage(
                "user",
                AssistantMessage.Role.ASSISTANT,
                "실시간 답변이 잠시 늦어져 저장된 개인 기록으로 먼저 도와드릴게요.",
                true
        );
        var live = new AssistantMessage(
                "user",
                AssistantMessage.Role.ASSISTANT,
                "지금 질문에 맞춘 정상 응답입니다.",
                true
        );

        assertThat(UserContextCollector.isGeneratedFallback(fallback)).isTrue();
        assertThat(UserContextCollector.isGeneratedFallback(live)).isFalse();
    }
}
