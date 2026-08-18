package app.morrow.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import app.morrow.auth.AuthRateLimiter;

@Service
@Transactional
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
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
                    userId,
                    promptBuilder.buildSystemPrompt(),
                    promptBuilder.buildUserContextPrompt(context),
                    content
            );
            mode = generated.mode();
            response = mode == OpenAIClient.Mode.LIVE
                    ? generated.content()
                    : promptBuilder.buildPersonalizedFallback(context, content);
        }
        var assistantMessage = new AssistantMessage(userId, AssistantMessage.Role.ASSISTANT, response, true);
        if (mode == OpenAIClient.Mode.LIVE) {
            assistantMessage = repository.save(assistantMessage);
        }
        return new AssistantReply(assistantMessage, mode, evidenceCount, evidenceCount > 0);
    }

    public ProactiveInsight generateProactiveInsight(String userId) {
        return generateProactiveInsight(userId, null);
    }

    public ProactiveInsight generateProactiveInsight(String userId, OffsetDateTime lastRecoveryAlertAt) {
        var context = contextCollector.collectProactiveContext(userId);
        var hasRecentSignals = !context.recentHealthSnapshots().isEmpty() || !context.recentCheckIns().isEmpty();
        if (!hasRecentSignals) {
            log.info("Proactive insight skipped: no recent signals. userId={}", userId);
            return ProactiveInsight.skip(OpenAIClient.Mode.FALLBACK, "NO_RECENT_SIGNALS");
        }

        var generated = openAIClient.generateShortResponse(
                userId,
                promptBuilder.buildSystemPrompt() + "\n\n" + promptBuilder.buildProactiveNotificationInstruction(),
                promptBuilder.buildUserContextPrompt(context),
                "지금 사용자에게 선제적 웰니스 알림이 필요한지 판단해 주세요. " + lastAlertNote(lastRecoveryAlertAt)
        );
        var content = generated.content() == null ? "" : generated.content().strip();
        if (generated.mode() != OpenAIClient.Mode.LIVE || content.isBlank() || isSkipDirective(content)) {
            log.info(
                    "Proactive insight skipped. userId={}, mode={}, skipDirective={}",
                    userId,
                    generated.mode(),
                    isSkipDirective(content)
            );
            return ProactiveInsight.skip(generated.mode(), "AI_SKIPPED");
        }
        if (content.length() > 100) {
            content = content.substring(0, 100).strip();
        }
        log.info("Proactive insight generated. userId={}, mode={}", userId, generated.mode());
        return new ProactiveInsight(true, actionTitle(content), content, generated.mode(), "RECENT_WELLNESS_CONTEXT");
    }

    /**
     * There is no fixed cooldown on AI recovery alerts; the model regulates its
     * own send frequency, so it must know how recently the user was notified.
     */
    static String lastAlertNote(OffsetDateTime lastRecoveryAlertAt) {
        if (lastRecoveryAlertAt == null) return "참고: 최근 발송한 회복 알림이 없습니다.";
        var minutes = Math.max(1, java.time.Duration.between(lastRecoveryAlertAt, OffsetDateTime.now()).toMinutes());
        if (minutes < 60) return "참고: 마지막 회복 알림을 약 " + minutes + "분 전에 보냈습니다.";
        var hours = minutes / 60;
        if (hours < 48) return "참고: 마지막 회복 알림을 약 " + hours + "시간 전에 보냈습니다.";
        return "참고: 마지막 회복 알림을 보낸 지 이틀 이상 지났습니다.";
    }

    /**
     * The proactive prompt asks the model to answer with exactly SKIP, but models
     * occasionally add punctuation or trailing whitespace (e.g. "SKIP.").
     * Anything that starts with SKIP and carries no other words counts as a skip,
     * so such variants never leak into a user-facing notification body.
     */
    static boolean isSkipDirective(String content) {
        var normalized = content.strip().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SKIP")) return false;
        return normalized.substring(4).chars().noneMatch(Character::isLetterOrDigit);
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
