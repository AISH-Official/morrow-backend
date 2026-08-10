package app.morrow.assistant;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    @Test
    void includesTheExactCurrentDateAndKoreanTimeZone() {
        var builder = new PromptBuilder("Asia/Seoul");
        var clock = Clock.fixed(Instant.parse("2026-08-10T02:30:00Z"), ZoneOffset.UTC);

        var prompt = builder.buildSystemPrompt(clock);

        assertThat(prompt)
                .contains("2026년 8월 10일 월요일 11:30 (Asia/Seoul)")
                .contains("오늘 날짜: 2026-08-10")
                .contains("상대적인 날짜")
                .contains("일반 지식, 학습, 업무, 기술, 일상 질문")
                .contains("답변 전체를 큰따옴표");
    }
}
