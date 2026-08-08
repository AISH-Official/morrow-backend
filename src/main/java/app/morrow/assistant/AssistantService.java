package app.morrow.assistant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
@Service @Transactional
public class AssistantService {
 private final AssistantMessageRepository repository; private final UserContextCollector contextCollector; private final PromptBuilder promptBuilder; private final SafetyFilter safetyFilter; private final OpenAIClient openAIClient;
 public AssistantService(AssistantMessageRepository repository,UserContextCollector contextCollector,PromptBuilder promptBuilder,SafetyFilter safetyFilter,OpenAIClient openAIClient){this.repository=repository;this.contextCollector=contextCollector;this.promptBuilder=promptBuilder;this.safetyFilter=safetyFilter;this.openAIClient=openAIClient;}
 public AssistantReply sendMessage(String userId,String content){repository.save(new AssistantMessage(userId,AssistantMessage.Role.USER,content,true));var safetyCheck=safetyFilter.check(content);String response;OpenAIClient.Mode mode;int evidenceCount=0;if(safetyCheck.blocked()){response=safetyCheck.responseOverride();mode=OpenAIClient.Mode.FALLBACK;}else{var context=contextCollector.collectContext(userId);evidenceCount=context.memories().stream().mapToInt(value->value.getEvidenceCount()).sum();var systemPrompt=promptBuilder.buildSystemPrompt();var userContextPrompt=promptBuilder.buildUserContextPrompt(context);var generated=openAIClient.generateResponse(systemPrompt,userContextPrompt,content);mode=generated.mode();response=generated.mode()==OpenAIClient.Mode.LIVE?generated.content():promptBuilder.buildPersonalizedFallback(context,content);}var assistantMessage=repository.save(new AssistantMessage(userId,AssistantMessage.Role.ASSISTANT,response,true));return new AssistantReply(assistantMessage,mode,evidenceCount,evidenceCount>0);}
 public List<AssistantMessage> getHistory(String userId,OffsetDateTime after){return repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId,after);}
 public OpenAIClient.Status status(){return openAIClient.status();}
 public record AssistantReply(AssistantMessage message,OpenAIClient.Mode mode,int personalizationEvidenceCount,boolean personalized){}
}
