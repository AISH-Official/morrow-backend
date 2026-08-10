package app.morrow.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIClientTest {
    @Test
    void removesStraightQuotesWrappingTheWholeResponse() {
        assertThat(OpenAIClient.normalizeContent("  \"오늘은 8월 10일입니다.\"  "))
                .isEqualTo("오늘은 8월 10일입니다.");
    }

    @Test
    void removesKoreanStyleQuotesWrappingTheWholeResponse() {
        assertThat(OpenAIClient.normalizeContent("“핵심 답변입니다.”"))
                .isEqualTo("핵심 답변입니다.");
    }

    @Test
    void preservesQuotesUsedInsideAResponse() {
        assertThat(OpenAIClient.normalizeContent("Morrow는 \"오늘\"을 서울 시간으로 계산합니다."))
                .isEqualTo("Morrow는 \"오늘\"을 서울 시간으로 계산합니다.");
    }
}
