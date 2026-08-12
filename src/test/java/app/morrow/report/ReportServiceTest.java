package app.morrow.report;

import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.RecommendationFeedback;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock CheckInRepository checkIns;
    @Mock RecoveryAttemptRepository recoveryAttempts;
    @Mock RecommendationFeedbackRepository recommendationFeedbacks;
    ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(checkIns, recoveryAttempts, recommendationFeedbacks, "Asia/Seoul");
        when(checkIns.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(eq("user"), any(OffsetDateTime.class))).thenReturn(List.of());
    }

    @Test
    void legacyRecommendationFeedbacksAreIncludedInWeeklyCompletions() {
        when(recoveryAttempts.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq("user"), any(OffsetDateTime.class))).thenReturn(List.of());
        when(recommendationFeedbacks.findForUserAfter(eq("user"), any(OffsetDateTime.class))).thenReturn(List.of(
                new RecommendationFeedback(UUID.randomUUID(), true, true, "도움이 됐어요"),
                new RecommendationFeedback(UUID.randomUUID(), true, false, "실행 후 큰 변화가 없었어요")
        ));

        var report = service.generateWeeklyReport("user");

        assertThat(report.suggestedRecoveryCount()).isEqualTo(2);
        assertThat(report.completedRecoveryCount()).isEqualTo(2);
        assertThat(report.recoveryHelpfulRate()).isEqualTo(50.0);
    }

    @Test
    void feedbackSavedWithARecoveryAttemptIsNotCountedTwice() {
        var attempt = new RecoveryAttempt("user", RecoveryAttempt.Action.BREATH, "WEB_STARTED", "test", "HIGH", RecoveryAttempt.Source.WEB);
        attempt.start();
        attempt.complete(RecoveryAttempt.Outcome.IMPROVED);
        when(recoveryAttempts.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq("user"), any(OffsetDateTime.class))).thenReturn(List.of(attempt));
        when(recommendationFeedbacks.findForUserAfter(eq("user"), any(OffsetDateTime.class))).thenReturn(List.of(
                new RecommendationFeedback(UUID.randomUUID(), true, true, "도움이 됐어요")
        ));

        var report = service.generateWeeklyReport("user");

        assertThat(report.suggestedRecoveryCount()).isEqualTo(1);
        assertThat(report.completedRecoveryCount()).isEqualTo(1);
        assertThat(report.recoveryHelpfulRate()).isEqualTo(100.0);
    }
}
