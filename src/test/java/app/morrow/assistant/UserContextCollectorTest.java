package app.morrow.assistant;

import app.morrow.auth.AccountAuthService;
import app.morrow.checkin.CheckInRepository;
import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.personalization.PersonalizationService;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import app.morrow.timeline.TimelineService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextCollectorTest {
    @Test
    void proactiveContextIncludesHealthDataOnConsentEvenWhenChatFlagIsOff() {
        var healthSnapshots = mock(HealthSignalSnapshotRepository.class);
        var checkIns = mock(CheckInRepository.class);
        var timelines = mock(TimelineService.class);
        var recommendations = mock(RecommendationRepository.class);
        var feedback = mock(RecommendationFeedbackRepository.class);
        var messages = mock(AssistantMessageRepository.class);
        var personalization = mock(PersonalizationService.class);
        var accounts = mock(AccountAuthService.class);
        var collector = new UserContextCollector(
                healthSnapshots, checkIns, timelines, recommendations, feedback, messages, personalization, accounts, false
        );
        var snapshot = new HealthSignalSnapshot(
                "user-a", "snapshot-a", HealthSignalSnapshot.Source.WATCH,
                300, 92.0, 81.0, 28.0, 1200.0, 80.0, 5.0, 500.0, 1.0, 18.0, 97.0, OffsetDateTime.now()
        );
        when(accounts.aiHealthConsent("user-a")).thenReturn(true);
        when(healthSnapshots.findTop12ByUserIdOrderByRecordedAtDesc("user-a")).thenReturn(List.of(snapshot));
        when(checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(anyString(), any())).thenReturn(List.of());
        when(timelines.findRecentByUserId(anyString(), any())).thenReturn(List.of());
        when(recommendations.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any())).thenReturn(List.of());
        when(messages.findTop24ByUserIdOrderByCreatedAtDesc(anyString())).thenReturn(List.of());
        when(personalization.activeMemories(anyString())).thenReturn(List.of());

        assertThat(collector.collectContext("user-a").recentHealthSnapshots()).isEmpty();
        assertThat(collector.collectProactiveContext("user-a").recentHealthSnapshots()).containsExactly(snapshot);
    }

    @Test
    void excludesGeneratedFallbackRepliesFromFutureAiContext() {
        var fallback = new AssistantMessage(
                "user",
                AssistantMessage.Role.ASSISTANT,
                "실시간 답변이 잠시 늦어져 저장된 개인 기록으로 먼저 도와드릴게요.",
                true
        );
        var live = new AssistantMessage(
                "user",
                AssistantMessage.Role.ASSISTANT,
                "지금 질문에 맞춘 정상 응답입니다.",
                true
        );

        assertThat(UserContextCollector.isGeneratedFallback(fallback)).isTrue();
        assertThat(UserContextCollector.isGeneratedFallback(live)).isFalse();
    }
}
