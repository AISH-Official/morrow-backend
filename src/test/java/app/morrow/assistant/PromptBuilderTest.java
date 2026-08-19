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

        var emptyContext = new UserContextCollector.UserContext(
                "time-user", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        var systemPrompt = builder.buildSystemPrompt();
        var contextPrompt = builder.buildUserContextPrompt(emptyContext, clock, false);

        assertThat(contextPrompt)
                .contains("2026년 8월 10일 월요일 11:30 (Asia/Seoul)")
                .contains("오늘 날짜: 2026-08-10")
                .contains("오늘, 어제, 내일, 요일");
        assertThat(systemPrompt)
                .contains("일반 지식, 학습, 업무, 기술, 일상 질문")
                .contains("최우선 출력 형식 규칙")
                .contains("큰따옴표 U+0022와 곡선형 큰따옴표 U+201C, U+201D는 어떤 경우에도 출력하지 않습니다")
                .contains("큰따옴표 없이 자연스러운 간접 표현으로 바꿔 씁니다")
                .contains("웹 검색어에는 사용자 식별자");
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

    @Test
    void keepsTheLatestStoredConversationInChronologicalPromptOrder() {
        var builder = new PromptBuilder("Asia/Seoul");
        var earlier = new AssistantMessage("chat-user", AssistantMessage.Role.USER, "내일 발표가 있어서 긴장돼", true);
        var latest = new AssistantMessage("chat-user", AssistantMessage.Role.ASSISTANT, "발표 전에 짧게 호흡해 보세요", true);
        var context = new UserContextCollector.UserContext(
                "chat-user",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(latest, earlier),
                List.of()
        );

        var prompt = builder.buildUserContextPrompt(context);

        assertThat(prompt)
                .contains("최근 대화:")
                .contains("사용자: 내일 발표가 있어서 긴장돼")
                .contains("AI: 발표 전에 짧게 호흡해 보세요");
        assertThat(prompt.indexOf("사용자: 내일 발표가 있어서 긴장돼"))
                .isLessThan(prompt.indexOf("AI: 발표 전에 짧게 호흡해 보세요"));
    }

    @Test
    void buildsRolePreservingConversationTurnsInChronologicalOrder() {
        var builder = new PromptBuilder("Asia/Seoul");
        var earlier = new AssistantMessage("chat-user", AssistantMessage.Role.USER, "어제 피곤했어", true);
        var latest = new AssistantMessage("chat-user", AssistantMessage.Role.ASSISTANT, "수면 기록을 같이 볼게요", true);
        var context = new UserContextCollector.UserContext(
                "chat-user", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(latest, earlier), List.of()
        );

        assertThat(builder.buildConversationTurns(context))
                .containsExactly(
                        new OpenAIClient.ConversationTurn("user", "어제 피곤했어"),
                        new OpenAIClient.ConversationTurn("assistant", "수면 기록을 같이 볼게요")
                );
    }

    @Test
    void clipsLongStoredTextBeforeSendingItToOpenAi() {
        var longMessage = "아주 긴 이전 대화 ".repeat(40);

        assertThat(PromptBuilder.clip(longMessage, 100))
                .hasSize(103)
                .endsWith("...")
                .doesNotContain(longMessage);
    }
}
