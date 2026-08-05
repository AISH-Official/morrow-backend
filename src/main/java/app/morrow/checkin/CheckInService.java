package app.morrow.checkin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
@Service @Transactional
public class CheckInService {
 private final CheckInRepository repository; public CheckInService(CheckInRepository repository){this.repository=repository;}
 public CheckIn create(CreateCheckIn input){return repository.save(new CheckIn(input.status(),input.cause(),input.note(),input.source(),input.recordedAt()==null?OffsetDateTime.now():input.recordedAt()));}
 public void delete(UUID id){repository.deleteById(id);} public void deleteAll(){repository.deleteAllInBatch();}
 public record CreateCheckIn(CheckIn.Status status,CheckIn.Cause cause,String note,CheckIn.Source source,OffsetDateTime recordedAt){}
}
