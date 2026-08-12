package app.morrow.recommendation;

import app.morrow.assistant.OpenAIClient;
import app.morrow.assistant.PromptBuilder;
import app.morrow.assistant.UserContextCollector;
import app.morrow.checkin.CheckIn;
import app.morrow.recovery.RecoveryActionDescriptor;
import app.morrow.recovery.RecoveryAttempt;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AIRecommendationService {
    private final OpenAIClient openAI;
    private final UserContextCollector contexts;
    private final PromptBuilder prompts;

    public AIRecommendationService(OpenAIClient openAI, UserContextCollector contexts, PromptBuilder prompts) {
        this.openAI = openAI;
        this.contexts = contexts;
        this.prompts = prompts;
    }

    public SuggestedRecommendation compose(
            String userId,
            CheckIn checkIn,
            String fallbackTitle,
            String fallbackRationale,
            boolean learned
    ) {
        var fallbackDescriptor = RecoveryActionDescriptor.fromTitle(fallbackTitle);
        var fallback = new SuggestedRecommendation(
                fallbackTitle,
                fallbackRationale,
                fallbackDescriptor.action(),
                fallbackDescriptor.durationSeconds(),
                learned ? Recommendation.Source.LEARNED : Recommendation.Source.RULE
        );
        var context = contexts.collectContext(userId);
        var generated = openAI.generateShortResponse(
                systemInstruction(),
                prompts.buildUserContextPrompt(context),
                userInstruction(checkIn, fallback)
        );
        if (generated.mode() != OpenAIClient.Mode.LIVE) return fallback;
        return parse(generated.content(), checkIn.getStatus())
                .map(value -> new SuggestedRecommendation(value.title(), value.rationale(), value.action(), value.durationSeconds(), Recommendation.Source.AI))
                .orElse(fallback);
    }

    private String systemInstruction() {
        return """
                당신은 Morrow의 Next Best Action 선택기입니다. 최근 체크인, 사용자의 명시적 선호, 수면·운동 요약, 과거 실행 결과를 비교해 지금 부담 없이 실행할 행동 하나만 고릅니다.
                사용할 수 있는 행동은 BREATH, WALK, WATER_WALK, STRETCH, FOCUS, SCREEN_BREAK뿐입니다.
                도움이 되지 않았던 행동은 피하고, 효과가 좋았던 행동과 사용자가 밝힌 선호를 우선합니다. 같은 행동을 기계적으로 반복하지 않습니다.
                불편함 상태에는 WALK, WATER_WALK, FOCUS를 고르지 않습니다. 진단, 치료, 약물 조언, 강한 운동을 제안하지 않습니다.
                제목은 사용자가 그대로 실행할 수 있는 자연스러운 한국어 문장으로, 근거는 어떤 최신 신호나 과거 반응을 반영했는지 짧게 씁니다.
                출력은 설명이나 마크다운 없이 반드시 다음 한 줄 형식만 사용합니다.
                ACTION|DURATION_SECONDS|TITLE|RATIONALE
                """;
    }

    private String userInstruction(CheckIn checkIn, SuggestedRecommendation fallback) {
        return """
                현재 체크인 상태: %s
                현재 체크인 원인: %s
                현재 메모: %s
                안전한 기본 추천: %s
                기본 실행: %s, %d초
                이 사용자의 현재 상황에 더 잘 맞는 한 가지를 선택하세요.
                """.formatted(
                checkIn.getStatus(),
                checkIn.getCause() == null ? "미기록" : checkIn.getCause(),
                checkIn.getNote() == null || checkIn.getNote().isBlank() ? "없음" : checkIn.getNote(),
                fallback.title(),
                fallback.action(),
                fallback.durationSeconds()
        );
    }

    static java.util.Optional<ParsedRecommendation> parse(String content, CheckIn.Status status) {
        if (content == null || content.isBlank()) return java.util.Optional.empty();
        for (var rawLine : content.lines().toList()) {
            var line = rawLine.strip().replace("`", "");
            if (line.startsWith("- ")) line = line.substring(2).strip();
            var parts = line.split("\\|", 4);
            if (parts.length != 4) continue;
            try {
                var action = RecoveryAttempt.Action.valueOf(parts[0].strip().toUpperCase(Locale.ROOT));
                var duration = Integer.parseInt(parts[1].strip());
                var title = parts[2].strip();
                var rationale = parts[3].strip();
                if (!safe(action, duration, title, rationale, status)) return java.util.Optional.empty();
                return java.util.Optional.of(new ParsedRecommendation(action, duration, title, rationale));
            } catch (RuntimeException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean safe(RecoveryAttempt.Action action, int duration, String title, String rationale, CheckIn.Status status) {
        if (title.isBlank() || title.length() > 100 || rationale.isBlank() || rationale.length() > 300) return false;
        if (duration < 30 || duration > maxDuration(action)) return false;
        return status != CheckIn.Status.UNCOMFORTABLE
                || (action != RecoveryAttempt.Action.WALK && action != RecoveryAttempt.Action.WATER_WALK && action != RecoveryAttempt.Action.FOCUS);
    }

    private static int maxDuration(RecoveryAttempt.Action action) {
        return switch (action) {
            case BREATH -> 300;
            case WALK -> 1_200;
            case WATER_WALK, STRETCH, SCREEN_BREAK -> 600;
            case FOCUS -> 1_800;
        };
    }

    public record SuggestedRecommendation(String title, String rationale, RecoveryAttempt.Action action, int durationSeconds, Recommendation.Source source) {}
    record ParsedRecommendation(RecoveryAttempt.Action action, int durationSeconds, String title, String rationale) {}
}
