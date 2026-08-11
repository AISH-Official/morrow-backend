package app.morrow.recommendation;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="recommendations")
public class Recommendation {
 @Id private UUID id; private String userId; @Column(length=200) private String title; @Column(length=500) private String rationale;
 @Enumerated(EnumType.STRING) private Status status; private OffsetDateTime createdAt;
 private UUID checkInId;
 protected Recommendation(){}
 public Recommendation(String userId,String title,String rationale,Status status){this.id=UUID.randomUUID();this.userId=userId;this.title=title;this.rationale=rationale;this.status=status;this.createdAt=OffsetDateTime.now();}
 public void linkToCheckIn(UUID value){this.checkInId=value;}
 public UUID getId(){return id;} public String getUserId(){return userId;} public String getTitle(){return title;} public String getRationale(){return rationale;} public Status getStatus(){return status;} public OffsetDateTime getCreatedAt(){return createdAt;}
 public UUID getCheckInId(){return checkInId;}
 public void applyFeedback(boolean completed,boolean helpful){this.status=completed?Status.COMPLETED:(helpful?Status.ACTIVE:Status.DISMISSED);}
 public enum Status{ACTIVE,COMPLETED,DISMISSED}
}
