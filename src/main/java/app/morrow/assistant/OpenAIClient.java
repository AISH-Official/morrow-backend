package app.morrow.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OpenAIClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(28);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FALLBACK_TIMEOUT = Duration.ofSeconds(18);
    private static final Set<String> NON_RETRYABLE_429_CODES = Set.of(
            "billing_hard_limit_reached",
            "credit_balance_exhausted",
            "insufficient_quota",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded"
    );

    private final String apiKey;
    private final String chatModel;
    private final String shortModel;
    private final String fallbackModel;
    private final boolean enabled;
    private final ResponsesGateway gateway;
    private final Sleeper sleeper;
    private final AtomicLong successfulRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong retriedRequests = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureType;
    private volatile Integer lastFailureStatus;
    private volatile String lastSuccessfulModel;

    @Autowired
    public OpenAIClient(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.chat-model:gpt-5.6-sol}") String chatModel,
            @Value("${openai.short-model:gpt-5.6-terra}") String shortModel,
            @Value("${openai.fallback-model:gpt-5.6-luna}") String fallbackModel,
            @Value("${openai.enabled:false}") boolean enabled,
            ObjectMapper objectMapper
    ) {
        this(
                apiKey,
                chatModel,
                shortModel,
                fallbackModel,
                enabled,
                new HttpResponsesGateway(apiKey, objectMapper),
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    OpenAIClient(
            String apiKey,
            String chatModel,
            String shortModel,
            String fallbackModel,
            boolean enabled,
            ResponsesGateway gateway,
            Sleeper sleeper
    ) {
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.shortModel = shortModel;
        this.fallbackModel = fallbackModel;
        this.enabled = enabled;
        this.gateway = gateway;
        this.sleeper = sleeper;
        log.info(
                "OpenAI assistant configured: enabled={}, keyConfigured={}, chatModel={}, shortModel={}, fallbackModel={}, api=responses",
                enabled,
                keyConfigured(),
                chatModel,
                shortModel,
                fallbackModel
        );
    }

    public GenerationResult generateResponse(
            String userId,
            String systemPrompt,
            String userContextPrompt,
            String userMessage
    ) {
        return generate(
                new Route(chatModel, "low", "medium", 1_200, CHAT_TIMEOUT),
                userId,
                systemPrompt,
                userContextPrompt,
                userMessage
        );
    }

    public GenerationResult generateShortResponse(
            String userId,
            String systemPrompt,
            String userContextPrompt,
            String userMessage
    ) {
        return generate(
                new Route(shortModel, "none", "low", 320, SHORT_TIMEOUT),
                userId,
                systemPrompt,
                userContextPrompt,
                userMessage
        );
    }

    private GenerationResult generate(
            Route primary,
            String userId,
            String systemPrompt,
            String userContextPrompt,
            String userMessage
    ) {
        if (!ready()) {
            log.warn("OpenAI assistant is using fallback mode because it is disabled or no API key is configured");
            return new GenerationResult(null, Mode.FALLBACK);
        }

        var instructions = systemPrompt + "\n\n" + userContextPrompt;
        var safetyIdentifier = safetyIdentifier(userId);
        var startedAt = System.nanoTime();

        try {
            return execute(primary, safetyIdentifier, instructions, userMessage, startedAt, 1);
        } catch (Exception primaryError) {
            if (!isRetryable(primaryError) || fallbackModel == null || fallbackModel.isBlank()) {
                return fail(primaryError, primary.model(), 1, startedAt);
            }

            retriedRequests.incrementAndGet();
            var delay = retryDelay();
            log.warn(
                    "OpenAI transient failure; switching model. type={}, status={}, code={}, model={}, retryModel={}, delayMs={}",
                    failureType(primaryError),
                    httpStatus(primaryError),
                    failureCode(primaryError),
                    primary.model(),
                    fallbackModel,
                    delay.toMillis()
            );
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return fail(interrupted, primary.model(), 1, startedAt);
            }

            var fallback = new Route(fallbackModel, "none", "low", primary.maxOutputTokens(), FALLBACK_TIMEOUT);
            try {
                return execute(fallback, safetyIdentifier, instructions, userMessage, startedAt, 2);
            } catch (Exception fallbackError) {
                return fail(fallbackError, fallbackModel, 2, startedAt);
            }
        }
    }

    private GenerationResult execute(
            Route route,
            String safetyIdentifier,
            String instructions,
            String input,
            long startedAt,
            int attempt
    ) throws IOException, InterruptedException {
        var response = gateway.create(
                new ResponseRequest(
                        route.model(),
                        instructions,
                        input,
                        route.maxOutputTokens(),
                        route.reasoningEffort(),
                        route.verbosity(),
                        safetyIdentifier
                ),
                route.timeout()
        );
        var content = normalizeContent(response.content());
        if (content == null || content.isBlank()) {
            throw new EmptyOpenAIResponseException();
        }
        recordSuccess(response.model() == null || response.model().isBlank() ? route.model() : response.model());
        log.debug(
                "OpenAI request succeeded. model={}, attempt={}, elapsedMs={}",
                lastSuccessfulModel,
                attempt,
                elapsedMillis(startedAt)
        );
        return new GenerationResult(content, Mode.LIVE);
    }

    private GenerationResult fail(Throwable error, String model, int attempt, long startedAt) {
        recordFailure(error);
        log.warn(
                "OpenAI request failed; using fallback mode. type={}, status={}, code={}, model={}, attempt={}, elapsedMs={}",
                failureType(error),
                httpStatus(error),
                failureCode(error),
                model,
                attempt,
                elapsedMillis(startedAt)
        );
        return new GenerationResult(null, Mode.FALLBACK);
    }

    private void recordSuccess(String model) {
        successfulRequests.incrementAndGet();
        consecutiveFailures.set(0);
        lastSuccessAt = Instant.now();
        lastSuccessfulModel = model;
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
            if (current instanceof OpenAIResponseException responseError) {
                if (responseError.status() == 429) {
                    return responseError.code() == null || !NON_RETRYABLE_429_CODES.contains(responseError.code());
                }
                return responseError.status() == 408
                        || responseError.status() == 409
                        || responseError.status() >= 500;
            }
            if (current instanceof EmptyOpenAIResponseException || current instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static Duration retryDelay() {
        return Duration.ofMillis(250L + ThreadLocalRandom.current().nextLong(100));
    }

    private static Integer httpStatus(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAIResponseException responseError) return responseError.status();
        }
        return null;
    }

    private static String failureCode(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAIResponseException responseError) return responseError.code();
        }
        return null;
    }

    private static String failureType(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAIResponseException || current instanceof IOException) {
                return current.getClass().getSimpleName();
            }
        }
        return error.getClass().getSimpleName();
    }

    static String safetyIdentifier(String userId) {
        var normalized = userId == null || userId.isBlank() ? "anonymous" : userId.strip();
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "morrow-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static String normalizeContent(String content) {
        if (content == null) return null;
        var normalized = content.strip();
        for (int attempt = 0; attempt < 2 && normalized.length() >= 2; attempt++) {
            var first = normalized.charAt(0);
            var last = normalized.charAt(normalized.length() - 1);
            var wrapped = (first == '"' && last == '"')
                    || (first == '“' && last == '”')
                    || (first == '‘' && last == '’');
            if (!wrapped) break;
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

    static String extractOutputText(JsonNode root) {
        return HttpResponsesGateway.extractOutputText(root);
    }

    public Status status() {
        return new Status(
                enabled,
                keyConfigured(),
                chatModel,
                shortModel,
                fallbackModel,
                ready(),
                recentState(),
                successfulRequests.get(),
                failedRequests.get(),
                retriedRequests.get(),
                consecutiveFailures.get(),
                lastSuccessAt,
                lastFailureAt,
                lastFailureType,
                lastFailureStatus,
                lastSuccessfulModel
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

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public enum Mode { LIVE, FALLBACK }

    public record GenerationResult(String content, Mode mode) {}

    public record Status(
            boolean enabled,
            boolean keyConfigured,
            String model,
            String shortModel,
            String fallbackModel,
            boolean ready,
            String recentState,
            long successfulRequests,
            long failedRequests,
            long retriedRequests,
            int consecutiveFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastFailureType,
            Integer lastFailureStatus,
            String lastSuccessfulModel
    ) {}

    record Route(String model, String reasoningEffort, String verbosity, int maxOutputTokens, Duration timeout) {}

    record ResponseRequest(
            String model,
            String instructions,
            String input,
            int maxOutputTokens,
            String reasoningEffort,
            String verbosity,
            String safetyIdentifier
    ) {}

    record ResponseResult(String content, String model) {}

    @FunctionalInterface
    interface ResponsesGateway {
        ResponseResult create(ResponseRequest request, Duration timeout) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final class OpenAIResponseException extends IOException {
        private final int status;
        private final String code;

        OpenAIResponseException(int status, String code) {
            super("OpenAI Responses API returned HTTP " + status);
            this.status = status;
            this.code = code;
        }

        int status() { return status; }
        String code() { return code; }
    }

    private static final class EmptyOpenAIResponseException extends IOException {}

    private static final class HttpResponsesGateway implements ResponsesGateway {
        private final String apiKey;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private HttpResponsesGateway(String apiKey, ObjectMapper objectMapper) {
            this.apiKey = apiKey;
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        }

        @Override
        public ResponseResult create(ResponseRequest request, Duration timeout) throws IOException, InterruptedException {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("model", request.model());
            payload.put("instructions", request.instructions());
            payload.put("input", request.input());
            payload.put("max_output_tokens", request.maxOutputTokens());
            payload.put("store", false);
            payload.put("safety_identifier", request.safetyIdentifier());
            payload.put("reasoning", Map.of(
                    "effort", request.reasoningEffort(),
                    "context", "current_turn"
            ));
            payload.put("text", Map.of("verbosity", request.verbosity()));

            var httpRequest = HttpRequest.newBuilder(RESPONSES_URI)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                var error = root.path("error");
                var code = textOrNull(error.path("code"));
                if (code == null) code = textOrNull(error.path("type"));
                throw new OpenAIResponseException(response.statusCode(), code);
            }
            return new ResponseResult(extractOutputText(root), textOrNull(root.path("model")));
        }

        static String extractOutputText(JsonNode root) {
            var combined = new StringBuilder();
            for (var output : root.path("output")) {
                if (!"message".equals(output.path("type").asText())) continue;
                for (var content : output.path("content")) {
                    if (!"output_text".equals(content.path("type").asText())) continue;
                    var text = textOrNull(content.path("text"));
                    if (text == null || text.isBlank()) continue;
                    if (!combined.isEmpty()) combined.append('\n');
                    combined.append(text);
                }
            }
            return combined.toString();
        }

        private static String textOrNull(JsonNode node) {
            return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
        }
    }
}
