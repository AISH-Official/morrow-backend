package app.morrow.timeline;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
@Entity @Table(name="timelines")
public class Timeline {
 @Id private UUID id; private String userId; private LocalTime time; @Column(length=200) private String title; @Column(length=500) private String detail;
 @Enumerated(EnumType.STRING) private Kind kind; private boolean userConfirmed; private OffsetDateTime occurredAt; private OffsetDateTime createdAt;
 protected Timeline(){}
 public Timeline(String userId,LocalTime time,String title,String detail,Kind kind,boolean userConfirmed,OffsetDateTime occurredAt){this.id=UUID.randomUUID();this.userId=userId;this.time=time;this.title=title;this.detail=detail;this.kind=kind;this.userConfirmed=userConfirmed;this.occurredAt=occurredAt;this.createdAt=OffsetDateTime.now();}
 public UUID getId(){return id;} public String getUserId(){return userId;} public LocalTime getTime(){return time;} public String getTitle(){return title;} public String getDetail(){return detail;} public Kind getKind(){return kind;} public boolean isUserConfirmed(){return userConfirmed;} public OffsetDateTime getOccurredAt(){return occurredAt;} public OffsetDateTime getCreatedAt(){return createdAt;}
 public LocalTime getDisplayTime(ZoneId zone){if(occurredAt!=null)return occurredAt.atZoneSameInstant(zone).toLocalTime();var offset=zone.getRules().getOffset(createdAt.toInstant()).getTotalSeconds();return time.plusSeconds(offset);}
 public void update(String title,String detail,Boolean userConfirmed){if(title!=null)this.title=title;if(detail!=null)this.detail=detail;if(userConfirmed!=null)this.userConfirmed=userConfirmed;}
 public enum Kind{SLEEP,CHECKIN,RECOVERY,ACTIVITY,INSIGHT}
}
