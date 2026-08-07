package app.morrow.api;
import app.morrow.assistant.AssistantMessage;
import app.morrow.assistant.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/assistant")
public class AssistantController {
 private final AssistantService service; public AssistantController(AssistantService service){this.service=service;}
 @PostMapping("/messages") ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest request){var response=service.sendMessage(request.userId(),request.content());return ResponseEntity.created(URI.create("/api/v1/assistant/messages/"+response.getId())).body(MessageResponse.from(response));}
 record MessageRequest(@NotBlank String userId,@NotBlank @Size(max=2000)String content){}
 record MessageResponse(UUID id,String userId,String role,String content,boolean safetyChecked,OffsetDateTime createdAt){static MessageResponse from(AssistantMessage m){return new MessageResponse(m.getId(),m.getUserId(),m.getRole().name(),m.getContent(),m.isSafetyChecked(),m.getCreatedAt());}}
}
