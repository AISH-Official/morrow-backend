package app.morrow.recommendation;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="recommendation_feedbacks")
public class RecommendationFeedback {
 @Id private UUID id; private UUID recommendationId; private boolean completed; private boolean helpful; @Column(length=500) private String note; private OffsetDateTime createdAt;
 protected RecommendationFeedback(){}
 public RecommendationFeedback(UUID recommendationId,boolean completed,boolean helpful,String note){this.id=UUID.randomUUID();this.recommendationId=recommendationId;this.completed=completed;this.helpful=helpful;this.note=note;this.createdAt=OffsetDateTime.now();}
 public UUID getId(){return id;} public UUID getRecommendationId(){return recommendationId;} public boolean isCompleted(){return completed;} public boolean isHelpful(){return helpful;} public String getNote(){return note;} public OffsetDateTime getCreatedAt(){return createdAt;}
}
