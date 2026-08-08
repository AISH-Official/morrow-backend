package app.morrow.assistant;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;

@Component
public class OpenAIClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public OpenAIClient(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.enabled:false}") boolean enabled
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
        log.info(
                "OpenAI assistant configured: enabled={}, keyConfigured={}, model={}",
                enabled,
                apiKey != null && !apiKey.isBlank(),
                model
        );
    }

    public GenerationResult generateResponse(String systemPrompt, String userContextPrompt, String userMessage) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI assistant is using fallback mode because it is disabled or no API key is configured");
            return new GenerationResult(null, Mode.FALLBACK);
        }

        try {
            var service = new OpenAiService(apiKey, Duration.ofSeconds(30));
            var messages = new ArrayList<ChatMessage>();
            messages.add(new ChatMessage("system", systemPrompt + "\n\n" + userContextPrompt));
            messages.add(new ChatMessage("user", userMessage));
            var request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.7)
                    .maxTokens(500)
                    .build();
            var completion = service.createChatCompletion(request);
            return new GenerationResult(completion.getChoices().get(0).getMessage().getContent(), Mode.LIVE);
        } catch (Exception error) {
            log.warn(
                    "OpenAI request failed; using fallback mode. type={}",
                    error.getClass().getSimpleName()
            );
            return new GenerationResult(null, Mode.FALLBACK);
        }
    }

    public Status status() { return new Status(enabled, apiKey != null && !apiKey.isBlank(), model, enabled && apiKey != null && !apiKey.isBlank()); }
    public enum Mode { LIVE, FALLBACK }
    public record GenerationResult(String content, Mode mode) {}
    public record Status(boolean enabled, boolean keyConfigured, String model, boolean ready) {}
}
