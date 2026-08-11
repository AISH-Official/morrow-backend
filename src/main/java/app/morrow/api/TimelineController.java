package app.morrow.api;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineService;
import app.morrow.auth.RequestUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
@RestController @RequestMapping("/api/v1/timeline")
public class TimelineController {
 private final TimelineService service; private final RequestUserResolver users; private final ZoneId timeZone; public TimelineController(TimelineService service,RequestUserResolver users,@Value("${morrow.time-zone:Asia/Seoul}")String timeZone){this.service=service;this.users=users;this.timeZone=ZoneId.of(timeZone);}
 @PatchMapping("/{id}") ResponseEntity<TimelineResponse> update(@PathVariable UUID id,@Valid @RequestBody UpdateRequest request){var updated=service.update(id,users.resolve("default-user"),new TimelineService.UpdateTimeline(request.title(),request.detail(),request.userConfirmed()));return ResponseEntity.ok(TimelineResponse.from(updated,timeZone));}
 @ExceptionHandler(TimelineService.TimelineNotFoundException.class) ResponseEntity<ErrorResponse> handleNotFound(TimelineService.TimelineNotFoundException ex){return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));}
 record UpdateRequest(@Size(max=200)String title,@Size(max=500)String detail,Boolean userConfirmed){}
 record TimelineResponse(UUID id,String userId,String time,String title,String detail,String kind,boolean userConfirmed,OffsetDateTime createdAt){static TimelineResponse from(Timeline t,ZoneId zone){return new TimelineResponse(t.getId(),t.getUserId(),t.getDisplayTime(zone).format(DateTimeFormatter.ofPattern("HH:mm")),t.getTitle(),t.getDetail(),t.getKind().name(),t.isUserConfirmed(),t.getCreatedAt());}}
 record ErrorResponse(String message){}
}
