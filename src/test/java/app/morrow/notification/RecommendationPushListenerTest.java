package app.morrow.notification;

import app.morrow.checkin.CheckInService;
import app.morrow.recommendation.Recommendation;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecommendationPushListenerTest {
    @Test
    void sendsTheRecommendationCreatedByCheckInFlow() {
        var notifications = mock(PushNotificationService.class);
        var recommendation = new Recommendation("user-a", "지금 잠깐 걸어요", "현재 체크인에 맞는 행동이에요.", Recommendation.Status.ACTIVE);

        new RecommendationPushListener(notifications)
                .onRecommendation(new CheckInService.CheckInRecommendationCreatedEvent(recommendation));

        verify(notifications).sendRecommendationAlert(recommendation);
    }
}
