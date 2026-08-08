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

    public String generateResponse(String systemPrompt, String userContextPrompt, String userMessage) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI assistant is using fallback mode because it is disabled or no API key is configured");
            return generateFallbackResponse();
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
            return completion.getChoices().get(0).getMessage().getContent();
        } catch (Exception error) {
            log.warn(
                    "OpenAI request failed; using fallback mode. type={}",
                    error.getClass().getSimpleName()
            );
            return generateFallbackResponse();
        }
    }

    private String generateFallbackResponse() {
        return "지금은 AI 연결을 사용할 수 없어 기본 안내로 답변하고 있어요. "
                + "잠시 후 다시 시도하거나 오늘의 체크인과 추천을 확인해 주세요.";
    }
}
