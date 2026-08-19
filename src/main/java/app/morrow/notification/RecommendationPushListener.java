package app.morrow.notification;

import app.morrow.checkin.CheckInService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RecommendationPushListener {
    private final PushNotificationService notifications;

    public RecommendationPushListener(PushNotificationService notifications) {
        this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecommendation(CheckInService.CheckInRecommendationCreatedEvent event) {
        notifications.sendRecommendationAlert(event.recommendation());
    }
}
