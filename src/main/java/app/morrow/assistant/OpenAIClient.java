package app.morrow.assistant;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OpenAIClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(18);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_ATTEMPTS = 2;
    private static final Set<String> NON_RETRYABLE_429_CODES = Set.of(
            "credit_balance_exhausted",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded"
    );

    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final OpenAiService chatService;
    private final OpenAiService shortService;
    private final Sleeper sleeper;
    private final AtomicLong successfulRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong retriedRequests = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureType;
    private volatile Integer lastFailureStatus;

    @Autowired
    public OpenAIClient(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.enabled:false}") boolean enabled
    ) {
        this(
                apiKey,
                model,
                enabled,
                createService(apiKey, enabled, CHAT_TIMEOUT),
                createService(apiKey, enabled, SHORT_TIMEOUT),
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    OpenAIClient(
            String apiKey,
            String model,
            boolean enabled,
            OpenAiService chatService,
            OpenAiService shortService,
            Sleeper sleeper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
        this.chatService = chatService;
        this.shortService = shortService;
        this.sleeper = sleeper;
        log.info(
                "OpenAI assistant configured: enabled={}, keyConfigured={}, model={}, pooledClients={}",
                enabled,
                keyConfigured(),
                model,
                chatService != null && shortService != null
        );
    }

    public GenerationResult generateResponse(String systemPrompt, String userContextPrompt, String userMessage) {
        return generateResponse(chatService, systemPrompt, userContextPrompt, userMessage, 900);
    }

    public GenerationResult generateShortResponse(String systemPrompt, String userContextPrompt, String userMessage) {
        return generateResponse(shortService, systemPrompt, userContextPrompt, userMessage, 240);
    }

    private GenerationResult generateResponse(
            OpenAiService service,
            String systemPrompt,
            String userContextPrompt,
            String userMessage,
            int maxTokens
    ) {
        if (!ready() || service == null) {
            log.warn("OpenAI assistant is using fallback mode because it is disabled or no API key is configured");
            return new GenerationResult(null, Mode.FALLBACK);
        }

        var messages = new ArrayList<ChatMessage>();
        messages.add(new ChatMessage("system", systemPrompt + "\n\n" + userContextPrompt));
        messages.add(new ChatMessage("user", userMessage));
        var request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.35)
                .maxTokens(maxTokens)
                .build();
        var startedAt = System.nanoTime();

        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var completion = service.createChatCompletion(request);
                var content = completion == null || completion.getChoices() == null || completion.getChoices().isEmpty()
                        ? null
                        : normalizeContent(completion.getChoices().get(0).getMessage().getContent());
                if (content == null || content.isBlank()) {
                    throw new EmptyOpenAIResponseException();
                }
                recordSuccess();
                log.debug("OpenAI request succeeded. attempt={}, elapsedMs={}", attempt, elapsedMillis(startedAt));
                return new GenerationResult(content, Mode.LIVE);
            } catch (Exception error) {
                var retryable = attempt < MAX_ATTEMPTS && isRetryable(error);
                if (retryable) {
                    retriedRequests.incrementAndGet();
                    var delay = retryDelay(attempt);
                    log.warn(
                            "OpenAI transient failure; retrying. type={}, status={}, attempt={}, delayMs={}",
                            failureType(error),
                            httpStatus(error),
                            attempt,
                            delay.toMillis()
                    );
                    try {
                        sleeper.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        recordFailure(interrupted);
                        return new GenerationResult(null, Mode.FALLBACK);
                    }
                    continue;
                }

                recordFailure(error);
                log.warn(
                        "OpenAI request failed; using fallback mode. type={}, status={}, attempt={}, elapsedMs={}",
                        failureType(error),
                        httpStatus(error),
                        attempt,
                        elapsedMillis(startedAt)
                );
                return new GenerationResult(null, Mode.FALLBACK);
            }
        }
        return new GenerationResult(null, Mode.FALLBACK);
    }

    private void recordSuccess() {
        successfulRequests.incrementAndGet();
        consecutiveFailures.set(0);
        lastSuccessAt = Instant.now();
    }

    private void recordFailure(Throwable error) {
        failedRequests.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        lastFailureAt = Instant.now();
        lastFailureType = failureType(error);
        lastFailureStatus = httpStatus(error);
    }

    static boolean isRetryable(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof EmptyOpenAIResponseException) {
                return true;
            }
            if (current instanceof OpenAiHttpException httpError) {
                if (httpError.statusCode == 429) {
                    return httpError.code == null || !NON_RETRYABLE_429_CODES.contains(httpError.code);
                }
                return httpError.statusCode == 408 || httpError.statusCode == 409 || httpError.statusCode >= 500;
            }
            if (current instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static Duration retryDelay(int attempt) {
        var baseMillis = 350L * (1L << Math.max(0, attempt - 1));
        return Duration.ofMillis(baseMillis + ThreadLocalRandom.current().nextLong(100));
    }

    private static Integer httpStatus(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAiHttpException httpError) {
                return httpError.statusCode;
            }
        }
        return null;
    }

    private static String failureType(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAiHttpException || current instanceof IOException) {
                return current.getClass().getSimpleName();
            }
        }
        return error.getClass().getSimpleName();
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static OpenAiService createService(String apiKey, boolean enabled, Duration timeout) {
        return enabled && apiKey != null && !apiKey.isBlank() ? new OpenAiService(apiKey, timeout) : null;
    }

    static String normalizeContent(String content) {
        if (content == null) {
            return null;
        }

        var normalized = content.strip();
        for (int attempt = 0; attempt < 2 && normalized.length() >= 2; attempt++) {
            var first = normalized.charAt(0);
            var last = normalized.charAt(normalized.length() - 1);
            var wrapped = (first == '"' && last == '"')
                    || (first == '“' && last == '”')
                    || (first == '‘' && last == '’');
            if (!wrapped) {
                break;
            }
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        return normalized
                .replace("\"", "")
                .replace("“", "")
                .replace("”", "")
                .replace("„", "")
                .replace("‟", "")
                .replace("＂", "")
                .strip();
    }

    public Status status() {
        return new Status(
                enabled,
                keyConfigured(),
                model,
                ready(),
                recentState(),
                successfulRequests.get(),
                failedRequests.get(),
                retriedRequests.get(),
                consecutiveFailures.get(),
                lastSuccessAt,
                lastFailureAt,
                lastFailureType,
                lastFailureStatus
        );
    }

    private boolean keyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private boolean ready() {
        return enabled && keyConfigured();
    }

    private String recentState() {
        if (!enabled) return "DISABLED";
        if (!keyConfigured()) return "MISCONFIGURED";
        if (lastSuccessAt == null && lastFailureAt == null) return "UNKNOWN";
        return consecutiveFailures.get() == 0 ? "HEALTHY" : "DEGRADED";
    }

    @PreDestroy
    void close() {
        shutdown(chatService);
        if (shortService != chatService) shutdown(shortService);
    }

    private void shutdown(OpenAiService service) {
        if (service == null) return;
        try {
            service.shutdownExecutor();
        } catch (RuntimeException error) {
            log.debug("OpenAI client shutdown failed. type={}", error.getClass().getSimpleName());
        }
    }

    public enum Mode { LIVE, FALLBACK }
    public record GenerationResult(String content, Mode mode) {}
    public record Status(
            boolean enabled,
            boolean keyConfigured,
            String model,
            boolean ready,
            String recentState,
            long successfulRequests,
            long failedRequests,
            long retriedRequests,
            int consecutiveFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastFailureType,
            Integer lastFailureStatus
    ) {}

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final class EmptyOpenAIResponseException extends RuntimeException {}
}
