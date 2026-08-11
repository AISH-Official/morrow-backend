package app.morrow.api;

import app.morrow.auth.RequestUserResolver;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recovery-attempts")
public class RecoveryAttemptController {
    private final RecoveryAttemptService service;
    private final RequestUserResolver users;

    public RecoveryAttemptController(RecoveryAttemptService service, RequestUserResolver users) { this.service = service; this.users = users; }

    @GetMapping
    List<RecoveryAttemptResponse> recent(@RequestParam(defaultValue = "default-user") String userId) {
        return service.recent(users.resolve(userId)).stream().map(RecoveryAttemptResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<RecoveryAttemptResponse> create(@Valid @RequestBody CreateRecoveryAttemptRequest request) {
        var value = service.createAndStart(users.resolve("default-user"), request.action(), request.triggerType(), request.reason(), request.confidence(), request.source());
        return ResponseEntity.created(URI.create("/api/v1/recovery-attempts/" + value.getId())).body(RecoveryAttemptResponse.from(value));
    }

    @PatchMapping("/{id}/start")
    RecoveryAttemptResponse start(@PathVariable UUID id) { return RecoveryAttemptResponse.from(service.start(id, users.resolve("default-user"))); }

    @PatchMapping("/{id}/complete")
    RecoveryAttemptResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteRecoveryAttemptRequest request) {
        return RecoveryAttemptResponse.from(service.complete(id, users.resolve("default-user"), request.outcome()));
    }

    record CreateRecoveryAttemptRequest(@NotNull RecoveryAttempt.Action action, String triggerType, String reason, String confidence, @NotNull RecoveryAttempt.Source source) {}
    record CompleteRecoveryAttemptRequest(@NotNull RecoveryAttempt.Outcome outcome) {}
    record RecoveryAttemptResponse(UUID id, String action, String triggerType, String reason, String confidence, String source, String status, String outcome, OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt) {
        static RecoveryAttemptResponse from(RecoveryAttempt value) {
            return new RecoveryAttemptResponse(value.getId(), value.getAction().name(), value.getTriggerType(), value.getReason(), value.getConfidence(), value.getSource().name(), value.getStatus().name(), value.getOutcome() == null ? null : value.getOutcome().name(), value.getCreatedAt(), value.getStartedAt(), value.getCompletedAt());
        }
    }
}
