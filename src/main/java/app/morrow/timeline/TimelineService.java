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
 public Timeline create(CreateTimeline input){return repository.save(new Timeline(input.userId(),input.time(),input.title(),input.detail(),input.kind(),input.userConfirmed()));}
 public Timeline update(UUID id,UpdateTimeline input){var timeline=repository.findById(id).orElseThrow(()->new TimelineNotFoundException(id));timeline.update(input.title(),input.detail(),input.userConfirmed());return timeline;}
 public List<Timeline> findRecentByUserId(String userId,OffsetDateTime after){return repository.findByUserIdAndCreatedAtAfterOrderByTimeAsc(userId,after);}
 public record CreateTimeline(String userId,LocalTime time,String title,String detail,Timeline.Kind kind,boolean userConfirmed){}
 public record UpdateTimeline(String title,String detail,Boolean userConfirmed){}
 public static class TimelineNotFoundException extends RuntimeException{public TimelineNotFoundException(UUID id){super("Timeline not found: "+id);}}
}
