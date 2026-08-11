package app.morrow.api;
import app.morrow.assistant.AssistantMessage;
import app.morrow.assistant.AssistantService;
import app.morrow.auth.RequestUserResolver;
import app.morrow.auth.AuthRateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/assistant")
public class AssistantController {
 private final AssistantService service; private final RequestUserResolver users; public AssistantController(AssistantService service,RequestUserResolver users){this.service=service;this.users=users;}
 @PostMapping("/messages") ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest request){var response=service.sendMessage(users.resolve(request.userId()),request.content());return ResponseEntity.created(URI.create("/api/v1/assistant/messages/"+response.message().getId())).body(MessageResponse.from(response));}
 @PostMapping("/proactive-insight") ProactiveInsightResponse proactiveInsight(@Valid @RequestBody ProactiveInsightRequest request){return ProactiveInsightResponse.from(service.generateProactiveInsight(users.resolve(request.userId())));}
 @GetMapping("/messages") java.util.List<MessageResponse> history(@RequestParam(defaultValue="default-user")String userId,@RequestParam(required=false)OffsetDateTime after){var since=after==null?OffsetDateTime.now().minusDays(7):after;return service.getHistory(users.resolve(userId),since).stream().map(MessageResponse::fromHistory).toList();}
 @DeleteMapping("/messages") ResponseEntity<Void> deleteConversation(@RequestParam(defaultValue="default-user")String userId){service.deleteConversation(users.resolve(userId));return ResponseEntity.noContent().build();}
 @GetMapping("/status") StatusResponse status(){var value=service.status();return new StatusResponse(value.enabled(),value.keyConfigured(),value.model(),value.ready());}
 @ExceptionHandler(AuthRateLimiter.TooManyAttemptsException.class) @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) ErrorResponse tooMany(AuthRateLimiter.TooManyAttemptsException error){return new ErrorResponse(error.getMessage());}
 record MessageRequest(@NotBlank String userId,@NotBlank @Size(max=2000)String content){}
 record ProactiveInsightRequest(@NotBlank @Size(max=100)String userId){}
 record MessageResponse(UUID id,String userId,String role,String content,boolean safetyChecked,OffsetDateTime createdAt,String aiMode,int personalizationEvidenceCount,boolean personalized){static MessageResponse from(AssistantService.AssistantReply r){var m=r.message();return new MessageResponse(m.getId(),m.getUserId(),m.getRole().name(),m.getContent(),m.isSafetyChecked(),m.getCreatedAt(),r.mode().name(),r.personalizationEvidenceCount(),r.personalized());}static MessageResponse fromHistory(AssistantMessage m){return new MessageResponse(m.getId(),m.getUserId(),m.getRole().name(),m.getContent(),m.isSafetyChecked(),m.getCreatedAt(),"STORED",0,false);}}
 record StatusResponse(boolean enabled,boolean keyConfigured,String model,boolean ready){}
 record ProactiveInsightResponse(boolean shouldNotify,String title,String body,String aiMode,String reason){static ProactiveInsightResponse from(AssistantService.ProactiveInsight value){return new ProactiveInsightResponse(value.shouldNotify(),value.title(),value.body(),value.mode().name(),value.reason());}}
 record ErrorResponse(String message){}
}
