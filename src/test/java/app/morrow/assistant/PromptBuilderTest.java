package app.morrow.assistant;

import app.morrow.health.HealthSignalSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;

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

    @Test
    void includesRecentAppleWatchMetricsWithSourceAndTimestamp() {
        var builder = new PromptBuilder("Asia/Seoul");
        var watchSnapshot = new HealthSignalSnapshot(
                "watch-user",
                "watch-snapshot-1",
                HealthSignalSnapshot.Source.WATCH,
                405,
                82.0,
                61.0,
                55.0,
                9234.0,
                412.0,
                38.0,
                6450.0,
                7.0,
                15.2,
                98.0,
                OffsetDateTime.parse("2026-08-10T08:15:00+09:00")
        );
        var context = new UserContextCollector.UserContext(
                "watch-user",
                List.of(watchSnapshot),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var prompt = builder.buildUserContextPrompt(context);

        assertThat(prompt)
                .contains("2026-08-10 08:15 · Apple Watch")
                .contains("수면 6시간 45분")
                .contains("안정 심박수 61bpm")
                .contains("HRV 55ms")
                .contains("걸음 9234걸음")
                .contains("활동 에너지 412kcal")
                .contains("거리 6.45km")
                .doesNotContain("아직 기록된 데이터가 없습니다");
    }
}
