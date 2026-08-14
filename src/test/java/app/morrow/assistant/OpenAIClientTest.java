package app.morrow.assistant;

import com.theokanning.openai.OpenAiError;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAIClientTest {
    @Test
    void removesStraightQuotesWrappingTheWholeResponse() {
        assertThat(OpenAIClient.normalizeContent("  \"오늘은 8월 10일입니다.\"  "))
                .isEqualTo("오늘은 8월 10일입니다.");
    }

    @Test
    void removesKoreanStyleQuotesWrappingTheWholeResponse() {
        assertThat(OpenAIClient.normalizeContent("“핵심 답변입니다.”"))
                .isEqualTo("핵심 답변입니다.");
    }

    @Test
    void removesDoubleQuotesAnywhereInAResponse() {
        assertThat(OpenAIClient.normalizeContent("Morrow는 \"오늘\"을 서울 시간으로 계산합니다."))
                .isEqualTo("Morrow는 오늘을 서울 시간으로 계산합니다.");
    }

    @Test
    void removesDecorativeQuotesFromMultipleSentences() {
        assertThat(OpenAIClient.normalizeContent("“오늘”은 월요일입니다. \"편하게\" 이야기해 주세요."))
                .isEqualTo("오늘은 월요일입니다. 편하게 이야기해 주세요.");
    }

    @Test
    void retriesATransientFailureAndRecoversWithTheSameClient() {
        var service = mock(OpenAiService.class);
        var sleeps = new AtomicInteger();
        when(service.createChatCompletion(any()))
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")))
                .thenReturn(completion("다시 연결됐어요."));
        var client = new OpenAIClient("test-key", "gpt-4o", true, service, service, ignored -> sleeps.incrementAndGet());

        var result = client.generateResponse("system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.LIVE);
        assertThat(result.content()).isEqualTo("다시 연결됐어요.");
        assertThat(sleeps).hasValue(1);
        assertThat(client.status().recentState()).isEqualTo("HEALTHY");
        assertThat(client.status().retriedRequests()).isEqualTo(1);
        assertThat(client.status().failedRequests()).isZero();
        verify(service, times(2)).createChatCompletion(any());
    }

    @Test
    void doesNotRetryAuthenticationFailures() {
        var service = mock(OpenAiService.class);
        when(service.createChatCompletion(any())).thenThrow(httpError(401, "invalid_api_key"));
        var client = new OpenAIClient("test-key", "gpt-4o", true, service, service, ignored -> {});

        var result = client.generateResponse("system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.FALLBACK);
        assertThat(client.status().recentState()).isEqualTo("DEGRADED");
        assertThat(client.status().failedRequests()).isEqualTo(1);
        assertThat(client.status().lastFailureStatus()).isEqualTo(401);
        verify(service).createChatCompletion(any());
    }

    @Test
    void doesNotRetryQuotaExhaustion() {
        assertThat(OpenAIClient.isRetryable(httpError(429, "credit_balance_exhausted"))).isFalse();
        assertThat(OpenAIClient.isRetryable(httpError(429, "rate_limit_exceeded"))).isTrue();
        assertThat(OpenAIClient.isRetryable(httpError(503, "server_error"))).isTrue();
    }

    @Test
    void retriesRateLimitedChatOnTheHigherCapacityMiniModel() {
        var service = mock(OpenAiService.class);
        when(service.createChatCompletion(any()))
                .thenThrow(httpError(429, "rate_limit_exceeded"))
                .thenReturn(completion("정상 응답입니다."));
        var client = new OpenAIClient("test-key", "gpt-4o", true, service, service, ignored -> {});

        var result = client.generateResponse("system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.LIVE);
        var order = inOrder(service);
        order.verify(service).createChatCompletion(org.mockito.ArgumentMatchers.argThat(
                request -> ((ChatCompletionRequest) request).getModel().equals("gpt-4o")
        ));
        order.verify(service).createChatCompletion(org.mockito.ArgumentMatchers.argThat(
                request -> ((ChatCompletionRequest) request).getModel().equals("gpt-4o-mini")
        ));
    }

    private ChatCompletionResult completion(String content) {
        var choice = new ChatCompletionChoice();
        choice.setMessage(new ChatMessage("assistant", content));
        var result = new ChatCompletionResult();
        result.setChoices(List.of(choice));
        return result;
    }

    private OpenAiHttpException httpError(int status, String code) {
        var details = new OpenAiError.OpenAiErrorDetails("request failed", "api_error", null, code);
        return new OpenAiHttpException(new OpenAiError(details), new RuntimeException("request failed"), status);
    }
}
