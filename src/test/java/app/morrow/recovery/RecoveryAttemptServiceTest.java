package app.morrow.recovery;

import app.morrow.personalization.PersonalizationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryAttemptServiceTest {
    private final RecoveryAttemptRepository repository = mock(RecoveryAttemptRepository.class);
    private final PersonalizationService personalization = mock(PersonalizationService.class);
    private final RecoveryAttemptService service = new RecoveryAttemptService(repository, personalization);

    @Test
    void createsAndStartsAnActionableRecoveryAttempt() {
        when(repository.save(any(RecoveryAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var value = service.createAndStart(
                "user-1",
                RecoveryAttempt.Action.BREATH,
                "TENSION_PATTERN",
                "긴장 체크인이 반복됐어요.",
                "HIGH",
                RecoveryAttempt.Source.WATCH
        );

        assertThat(value.getStatus()).isEqualTo(RecoveryAttempt.Status.STARTED);
        assertThat(value.getStartedAt()).isNotNull();
        assertThat(value.getReason()).contains("긴장");
    }

    @Test
    void completingAnAttemptLearnsItsOutcome() {
        var value = new RecoveryAttempt("user-1", RecoveryAttempt.Action.STRETCH, "LOW_ACTIVITY", "움직임이 적어요.", "MEDIUM", RecoveryAttempt.Source.WEB);
        value.start();
        when(repository.findById(value.getId())).thenReturn(Optional.of(value));

        var completed = service.complete(value.getId(), "user-1", RecoveryAttempt.Outcome.IMPROVED);

        assertThat(completed.getStatus()).isEqualTo(RecoveryAttempt.Status.COMPLETED);
        assertThat(completed.getOutcome()).isEqualTo(RecoveryAttempt.Outcome.IMPROVED);
        verify(personalization).learnFromRecoveryOutcome("user-1", RecoveryAttempt.Action.STRETCH, RecoveryAttempt.Outcome.IMPROVED);
    }
}
