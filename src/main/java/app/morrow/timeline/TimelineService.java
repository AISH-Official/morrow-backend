package app.morrow.timeline;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
@Service @Transactional
public class TimelineService {
 private final TimelineRepository repository; public TimelineService(TimelineRepository repository){this.repository=repository;}
 public Timeline create(CreateTimeline input){return repository.save(new Timeline(input.userId(),input.time(),input.title(),input.detail(),input.kind(),input.userConfirmed(),input.occurredAt()));}
 public Timeline update(UUID id,String userId,UpdateTimeline input){var timeline=repository.findById(id).filter(value->value.getUserId().equals(userId)).orElseThrow(()->new TimelineNotFoundException(id));timeline.update(input.title(),input.detail(),input.userConfirmed());return timeline;}
 public List<Timeline> findRecentByUserId(String userId,OffsetDateTime after){return repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId,after);}
 public void deleteForCheckIn(String userId,OffsetDateTime recordedAt){repository.deleteByUserIdAndKindAndOccurredAt(userId,Timeline.Kind.CHECKIN,recordedAt);}
 public record CreateTimeline(String userId,LocalTime time,String title,String detail,Timeline.Kind kind,boolean userConfirmed,OffsetDateTime occurredAt){}
 public record UpdateTimeline(String title,String detail,Boolean userConfirmed){}
 public static class TimelineNotFoundException extends RuntimeException{public TimelineNotFoundException(UUID id){super("Timeline not found: "+id);}}
}
