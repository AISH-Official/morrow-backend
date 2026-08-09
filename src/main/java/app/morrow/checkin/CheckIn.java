package app.morrow.checkin;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="check_ins",uniqueConstraints=@UniqueConstraint(name="uk_check_in_client_event",columnNames={"user_id","client_event_id"}))
public class CheckIn {
 @Id private UUID id; @Column(name="user_id") private String userId; @Column(name="client_event_id",length=100) private String clientEventId; @Enumerated(EnumType.STRING) private Status status; @Enumerated(EnumType.STRING) private Cause cause;
 @Column(length=500) private String note; @Enumerated(EnumType.STRING) private Source source; private OffsetDateTime recordedAt;
 protected CheckIn(){}
 public CheckIn(String userId,Status status,Cause cause,String note,Source source,OffsetDateTime recordedAt){this(userId,null,status,cause,note,source,recordedAt);}
 public CheckIn(String userId,String clientEventId,Status status,Cause cause,String note,Source source,OffsetDateTime recordedAt){this.id=UUID.randomUUID();this.userId=userId;this.clientEventId=clientEventId;this.status=status;this.cause=cause;this.note=note;this.source=source;this.recordedAt=recordedAt;}
 public UUID getId(){return id;} public String getUserId(){return userId;} public String getClientEventId(){return clientEventId;} public Status getStatus(){return status;} public Cause getCause(){return cause;} public String getNote(){return note;} public Source getSource(){return source;} public OffsetDateTime getRecordedAt(){return recordedAt;}
 public enum Status{OK,TENSE,TIRED,LOW_FOCUS,UNCOMFORTABLE} public enum Cause{SLEEP,WORK,STUDY,RELATIONSHIP,PHYSICAL,UNKNOWN} public enum Source{WATCH,IPHONE,WEB}
}
