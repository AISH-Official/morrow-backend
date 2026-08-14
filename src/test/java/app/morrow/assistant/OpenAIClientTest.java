package app.morrow.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
    void switchesFromSolToLunaAfterATransientFailure() {
        var requests = new ArrayList<OpenAIClient.ResponseRequest>();
        var calls = new AtomicInteger();
        var sleeps = new AtomicInteger();
        OpenAIClient.ResponsesGateway gateway = (request, timeout) -> {
            requests.add(request);
            if (calls.getAndIncrement() == 0) throw new SocketTimeoutException("timeout");
            return new OpenAIClient.ResponseResult("다시 연결됐어요.", request.model());
        };
        var client = client(gateway, ignored -> sleeps.incrementAndGet());

        var result = client.generateResponse("user-1", "system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.LIVE);
        assertThat(result.content()).isEqualTo("다시 연결됐어요.");
        assertThat(requests).extracting(OpenAIClient.ResponseRequest::model)
                .containsExactly("gpt-5.6-sol", "gpt-5.6-luna");
        assertThat(sleeps).hasValue(1);
        assertThat(client.status().recentState()).isEqualTo("HEALTHY");
        assertThat(client.status().retriedRequests()).isEqualTo(1);
        assertThat(client.status().failedRequests()).isZero();
        assertThat(client.status().lastSuccessfulModel()).isEqualTo("gpt-5.6-luna");
    }

    @Test
    void doesNotRetryAuthenticationFailures() {
        var calls = new AtomicInteger();
        OpenAIClient.ResponsesGateway gateway = (request, timeout) -> {
            calls.incrementAndGet();
            throw new OpenAIClient.OpenAIResponseException(401, "invalid_api_key");
        };
        var client = client(gateway, ignored -> {});

        var result = client.generateResponse("user-1", "system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.FALLBACK);
        assertThat(calls).hasValue(1);
        assertThat(client.status().recentState()).isEqualTo("DEGRADED");
        assertThat(client.status().lastFailureStatus()).isEqualTo(401);
    }

    @Test
    void doesNotRetryQuotaExhaustion() {
        assertThat(OpenAIClient.isRetryable(responseError(429, "insufficient_quota"))).isFalse();
        assertThat(OpenAIClient.isRetryable(responseError(429, "rate_limit_exceeded"))).isTrue();
        assertThat(OpenAIClient.isRetryable(responseError(503, "server_error"))).isTrue();
    }

    @Test
    void switchesFromSolToLunaWhenSolIsRateLimited() {
        var requests = new ArrayList<OpenAIClient.ResponseRequest>();
        OpenAIClient.ResponsesGateway gateway = (request, timeout) -> {
            requests.add(request);
            if (requests.size() == 1) {
                throw new OpenAIClient.OpenAIResponseException(429, "rate_limit_exceeded");
            }
            return new OpenAIClient.ResponseResult("정상 응답입니다.", request.model());
        };
        var client = client(gateway, ignored -> {});

        var result = client.generateResponse("user-1", "system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.LIVE);
        assertThat(requests).extracting(OpenAIClient.ResponseRequest::model)
                .containsExactly("gpt-5.6-sol", "gpt-5.6-luna");
    }

    @Test
    void shortResponsesUseTerraWithLowLatencySettingsAndAnonymousSafetyId() {
        var requests = new ArrayList<OpenAIClient.ResponseRequest>();
        var timeouts = new ArrayList<Duration>();
        OpenAIClient.ResponsesGateway gateway = (request, timeout) -> {
            requests.add(request);
            timeouts.add(timeout);
            return new OpenAIClient.ResponseResult("물을 한 잔 마셔요.", request.model());
        };
        var client = client(gateway, ignored -> {});

        var result = client.generateShortResponse("person@example.com", "system", "context", "message");

        assertThat(result.mode()).isEqualTo(OpenAIClient.Mode.LIVE);
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.model()).isEqualTo("gpt-5.6-terra");
            assertThat(request.reasoningEffort()).isEqualTo("none");
            assertThat(request.verbosity()).isEqualTo("low");
            assertThat(request.maxOutputTokens()).isEqualTo(320);
            assertThat(request.safetyIdentifier()).startsWith("morrow-");
            assertThat(request.safetyIdentifier()).doesNotContain("person@example.com");
        });
        assertThat(timeouts).containsExactly(Duration.ofSeconds(10));
    }

    @Test
    void safetyIdentifierIsStableWithoutExposingTheUserId() {
        var first = OpenAIClient.safetyIdentifier("private-user-id");
        var second = OpenAIClient.safetyIdentifier("private-user-id");

        assertThat(first).isEqualTo(second).startsWith("morrow-");
        assertThat(first).doesNotContain("private-user-id");
    }

    @Test
    void extractsTextFromResponsesApiMessages() throws Exception {
        var root = new ObjectMapper().readTree("""
                {
                  "model": "gpt-5.6-sol",
                  "output": [{
                    "type": "message",
                    "content": [
                      {"type": "output_text", "text": "첫 문장"},
                      {"type": "output_text", "text": "둘째 문장"}
                    ]
                  }]
                }
                """);

        assertThat(OpenAIClient.extractOutputText(root)).isEqualTo("첫 문장\n둘째 문장");
    }

    private OpenAIClient client(OpenAIClient.ResponsesGateway gateway, OpenAIClient.Sleeper sleeper) {
        return new OpenAIClient(
                "test-key",
                "gpt-5.6-sol",
                "gpt-5.6-terra",
                "gpt-5.6-luna",
                true,
                gateway,
                sleeper
        );
    }

    private OpenAIClient.OpenAIResponseException responseError(int status, String code) {
        return new OpenAIClient.OpenAIResponseException(status, code);
    }
}
