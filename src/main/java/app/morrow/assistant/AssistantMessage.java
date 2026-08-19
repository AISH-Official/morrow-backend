package app.morrow.assistant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="assistant_messages")
public class AssistantMessage {
 @Id private UUID id; private String userId; @Enumerated(EnumType.STRING) private Role role; @Column(columnDefinition="TEXT") private String content; private boolean safetyChecked; private OffsetDateTime createdAt;
 protected AssistantMessage(){}
 public AssistantMessage(String userId,Role role,String content,boolean safetyChecked){this.id=UUID.randomUUID();this.userId=userId;this.role=role;this.content=content;this.safetyChecked=safetyChecked;this.createdAt=OffsetDateTime.now();}
 public UUID getId(){return id;} public String getUserId(){return userId;} public Role getRole(){return role;} public String getContent(){return content;} public boolean isSafetyChecked(){return safetyChecked;} public OffsetDateTime getCreatedAt(){return createdAt;}
 public enum Role{USER,ASSISTANT,SYSTEM}
}
