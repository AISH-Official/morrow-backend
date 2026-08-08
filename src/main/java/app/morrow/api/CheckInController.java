package app.morrow.api;
import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/check-ins")
public class CheckInController {
 private final CheckInService service; public CheckInController(CheckInService service){this.service=service;}
 @PostMapping ResponseEntity<CheckInResponse> create(@Valid @RequestBody CreateRequest request){var saved=service.create(new CheckInService.CreateCheckIn(request.userId(),request.status(),request.cause(),request.note(),request.source(),request.recordedAt()));return ResponseEntity.created(URI.create("/api/v1/check-ins/"+saved.getId())).body(CheckInResponse.from(saved));}
 @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id){service.delete(id);return ResponseEntity.noContent().build();}
 record CreateRequest(@Size(max=100)String userId,@NotNull CheckIn.Status status,CheckIn.Cause cause,@Size(max=500)String note,@NotNull CheckIn.Source source,OffsetDateTime recordedAt){}
 record CheckInResponse(UUID id,String userId,String status,String cause,String note,String source,OffsetDateTime recordedAt){static CheckInResponse from(CheckIn c){return new CheckInResponse(c.getId(),c.getUserId(),c.getStatus().name(),c.getCause()==null?null:c.getCause().name(),c.getNote(),c.getSource().name(),c.getRecordedAt());}}
}
