package app.morrow.api;

import app.morrow.personalization.PersonalizationService;
import app.morrow.personalization.UserMemory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/personalization")
public class PersonalizationController {
    private final PersonalizationService service;
    public PersonalizationController(PersonalizationService service) { this.service = service; }

    @GetMapping("/profile") ProfileResponse profile(@RequestParam(defaultValue = "default-user") String userId) { return ProfileResponse.from(service.profile(userId)); }
    @GetMapping("/memories") List<MemoryResponse> memories(@RequestParam(defaultValue = "default-user") String userId) { return service.activeMemories(userId).stream().map(MemoryResponse::from).toList(); }
    @PostMapping("/memories") ResponseEntity<MemoryResponse> create(@Valid @RequestBody CreateMemoryRequest request) {
        var saved = service.createDeclaredMemory(request.userId(), request.type(), request.summary());
        return ResponseEntity.created(URI.create("/api/v1/personalization/memories/" + saved.getId())).body(MemoryResponse.from(saved));
    }
    @PatchMapping("/memories/{id}") MemoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMemoryRequest request) { return MemoryResponse.from(service.updateMemory(id, request.userId(), request.summary(), request.active())); }
    @DeleteMapping("/memories/{id}") ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(defaultValue = "default-user") String userId) { service.deleteMemory(id, userId); return ResponseEntity.noContent().build(); }
    @PostMapping("/rebuild") ProfileResponse rebuild(@RequestParam(defaultValue = "default-user") String userId) { return ProfileResponse.from(service.rebuild(userId)); }

    @ExceptionHandler({PersonalizationService.MemoryNotFoundException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> handle(RuntimeException error) { return ResponseEntity.badRequest().body(new ErrorResponse(error.getMessage())); }

    record CreateMemoryRequest(@NotBlank @Size(max = 100) String userId, @NotNull UserMemory.Type type, @NotBlank @Size(max = 600) String summary) {}
    record UpdateMemoryRequest(@NotBlank @Size(max = 100) String userId, @Size(max = 600) String summary, Boolean active) {}
    record ProfileResponse(String userId, int activeMemoryCount, int evidenceCount, long helpfulStrategyCount, long avoidStrategyCount, OffsetDateTime lastLearnedAt, boolean personalized) {
        static ProfileResponse from(PersonalizationService.Profile value) { return new ProfileResponse(value.userId(), value.activeMemoryCount(), value.evidenceCount(), value.helpfulStrategyCount(), value.avoidStrategyCount(), value.lastLearnedAt(), value.personalized()); }
    }
    record MemoryResponse(UUID id, String type, String summary, int positiveEvidence, int negativeEvidence, int evidenceCount, double confidence, String source, OffsetDateTime updatedAt) {
        static MemoryResponse from(UserMemory value) { return new MemoryResponse(value.getId(), value.getType().name(), value.getSummary(), value.getPositiveEvidence(), value.getNegativeEvidence(), value.getEvidenceCount(), value.getConfidence(), value.getSource().name(), value.getUpdatedAt()); }
    }
    record ErrorResponse(String message) {}
}
