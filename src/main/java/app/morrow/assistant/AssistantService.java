package app.morrow.assistant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
@Service @Transactional
public class AssistantService {
 private final AssistantMessageRepository repository; private final UserContextCollector contextCollector; private final PromptBuilder promptBuilder; private final SafetyFilter safetyFilter; private final OpenAIClient openAIClient;
 public AssistantService(AssistantMessageRepository repository,UserContextCollector contextCollector,PromptBuilder promptBuilder,SafetyFilter safetyFilter,OpenAIClient openAIClient){this.repository=repository;this.contextCollector=contextCollector;this.promptBuilder=promptBuilder;this.safetyFilter=safetyFilter;this.openAIClient=openAIClient;}
 public AssistantMessage sendMessage(String userId,String content){var userMessage=repository.save(new AssistantMessage(userId,AssistantMessage.Role.USER,content,true));var safetyCheck=safetyFilter.check(content);String response;if(safetyCheck.blocked()){response=safetyCheck.responseOverride();}else{var context=contextCollector.collectContext(userId);var systemPrompt=promptBuilder.buildSystemPrompt();var userContextPrompt=promptBuilder.buildUserContextPrompt(context);response=openAIClient.generateResponse(systemPrompt,userContextPrompt,content);}var assistantMessage=repository.save(new AssistantMessage(userId,AssistantMessage.Role.ASSISTANT,response,true));return assistantMessage;}
 public List<AssistantMessage> getHistory(String userId,OffsetDateTime after){return repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId,after);}
}
