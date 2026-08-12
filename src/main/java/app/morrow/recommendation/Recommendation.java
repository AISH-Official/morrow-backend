package app.morrow.recommendation;
import app.morrow.recovery.RecoveryActionDescriptor;
import app.morrow.recovery.RecoveryAttempt;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="recommendations")
public class Recommendation {
 @Id private UUID id; private String userId; @Column(length=200) private String title; @Column(length=500) private String rationale;
 @Enumerated(EnumType.STRING) private Status status; private OffsetDateTime createdAt;
 @Enumerated(EnumType.STRING) private RecoveryAttempt.Action action;
 private Integer durationSeconds;
 @Enumerated(EnumType.STRING) private Source source;
 private UUID checkInId;
 protected Recommendation(){}
 public Recommendation(String userId,String title,String rationale,Status status){
  this(userId,title,rationale,status,null,null,Source.RULE);
 }
 public Recommendation(String userId,String title,String rationale,Status status,RecoveryAttempt.Action action,Integer durationSeconds,Source source){
  var descriptor=RecoveryActionDescriptor.fromTitle(title);
  this.id=UUID.randomUUID();this.userId=userId;this.title=title;this.rationale=rationale;this.status=status;this.createdAt=OffsetDateTime.now();
  this.action=action==null?descriptor.action():action;
  this.durationSeconds=durationSeconds==null||durationSeconds<30?descriptor.durationSeconds():durationSeconds;
  this.source=source==null?Source.RULE:source;
 }
 public void linkToCheckIn(UUID value){this.checkInId=value;}
 public UUID getId(){return id;} public String getUserId(){return userId;} public String getTitle(){return title;} public String getRationale(){return rationale;} public Status getStatus(){return status;} public OffsetDateTime getCreatedAt(){return createdAt;}
 public RecoveryAttempt.Action getAction(){return action==null?RecoveryActionDescriptor.fromTitle(title).action():action;}
 public int getDurationSeconds(){return durationSeconds==null||durationSeconds<30?RecoveryActionDescriptor.fromTitle(title).durationSeconds():durationSeconds;}
 public Source getSource(){return source==null?Source.RULE:source;}
 public UUID getCheckInId(){return checkInId;}
 public void applyFeedback(boolean completed,boolean helpful){this.status=completed?Status.COMPLETED:(helpful?Status.ACTIVE:Status.DISMISSED);}
 public enum Status{ACTIVE,COMPLETED,DISMISSED}
 public enum Source{AI,LEARNED,RULE}
}
