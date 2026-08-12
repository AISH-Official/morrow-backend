package app.morrow.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import app.morrow.auth.AuthRateLimiter;

@Service
@Transactional
public class AssistantService {
    private final AssistantMessageRepository repository;
    private final UserContextCollector contextCollector;
    private final PromptBuilder promptBuilder;
    private final SafetyFilter safetyFilter;
    private final OpenAIClient openAIClient;
    private final AuthRateLimiter rateLimiter;

    public AssistantService(
            AssistantMessageRepository repository,
            UserContextCollector contextCollector,
            PromptBuilder promptBuilder,
            SafetyFilter safetyFilter,
            OpenAIClient openAIClient,
            AuthRateLimiter rateLimiter
    ) {
        this.repository = repository;
        this.contextCollector = contextCollector;
        this.promptBuilder = promptBuilder;
        this.safetyFilter = safetyFilter;
        this.openAIClient = openAIClient;
        this.rateLimiter = rateLimiter;
    }

    public AssistantReply sendMessage(String userId, String content) {
        rateLimiter.check("assistant:" + userId, 20);
        var safetyCheck = safetyFilter.check(content);
        var context = safetyCheck.blocked() ? null : contextCollector.collectContext(userId);
        repository.save(new AssistantMessage(userId, AssistantMessage.Role.USER, content, true));
        String response;
        OpenAIClient.Mode mode;
        int evidenceCount = 0;
        if (safetyCheck.blocked()) {
            response = safetyCheck.responseOverride();
            mode = OpenAIClient.Mode.FALLBACK;
        } else {
            evidenceCount = context.memories().stream().mapToInt(value -> value.getEvidenceCount()).sum();
            var generated = openAIClient.generateResponse(
                    promptBuilder.buildSystemPrompt(),
                    promptBuilder.buildUserContextPrompt(context),
                    content
            );
            mode = generated.mode();
            response = mode == OpenAIClient.Mode.LIVE
                    ? generated.content()
                    : promptBuilder.buildPersonalizedFallback(context, content);
        }
        var assistantMessage = repository.save(new AssistantMessage(userId, AssistantMessage.Role.ASSISTANT, response, true));
        return new AssistantReply(assistantMessage, mode, evidenceCount, evidenceCount > 0);
    }

    public ProactiveInsight generateProactiveInsight(String userId) {
        var context = contextCollector.collectContext(userId);
        var hasRecentSignals = !context.recentHealthSnapshots().isEmpty() || !context.recentCheckIns().isEmpty();
        if (!hasRecentSignals) {
            return ProactiveInsight.skip(OpenAIClient.Mode.FALLBACK, "NO_RECENT_SIGNALS");
        }

        var generated = openAIClient.generateShortResponse(
                promptBuilder.buildSystemPrompt() + "\n\n" + promptBuilder.buildProactiveNotificationInstruction(),
                promptBuilder.buildUserContextPrompt(context),
                "지금 사용자에게 선제적 웰니스 알림이 필요한지 판단해 주세요."
        );
        var content = generated.content() == null ? "" : generated.content().strip();
        if (generated.mode() != OpenAIClient.Mode.LIVE || content.equalsIgnoreCase("SKIP") || content.isBlank()) {
            return ProactiveInsight.skip(generated.mode(), "AI_SKIPPED");
        }
        if (content.length() > 100) {
            content = content.substring(0, 100).strip();
        }
        return new ProactiveInsight(true, actionTitle(content), content, generated.mode(), "RECENT_WELLNESS_CONTEXT");
    }

    private String actionTitle(String content) {
        if (content.contains("호흡") || content.contains("숨")) return "지금 1분 호흡해요";
        if (content.contains("걷") || content.contains("산책")) return "지금 잠깐 걸어볼까요?";
        if (content.contains("물")) return "지금 물 한 잔 어때요?";
        if (content.contains("스트레칭") || content.contains("어깨")) return "지금 몸을 가볍게 풀어요";
        if (content.contains("집중")) return "지금 짧게 시작해 볼까요?";
        return "지금 바로 해볼 한 가지";
    }

    public List<AssistantMessage> getHistory(String userId, OffsetDateTime after) {
        return repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, after);
    }

    public void deleteConversation(String userId) { repository.deleteByUserId(userId); }

    public OpenAIClient.Status status() {
        return openAIClient.status();
    }

    public record AssistantReply(
            AssistantMessage message,
            OpenAIClient.Mode mode,
            int personalizationEvidenceCount,
            boolean personalized
    ) {}

    public record ProactiveInsight(
            boolean shouldNotify,
            String title,
            String body,
            OpenAIClient.Mode mode,
            String reason
    ) {
        static ProactiveInsight skip(OpenAIClient.Mode mode, String reason) {
            return new ProactiveInsight(false, "", "", mode, reason);
        }
    }
}
