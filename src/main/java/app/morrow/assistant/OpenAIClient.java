package app.morrow.assistant;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
@Component
public class OpenAIClient {
 private final String apiKey; private final String model; private final boolean enabled;
 public OpenAIClient(@Value("${openai.api.key:}") String apiKey,@Value("${openai.model:gpt-4o-mini}") String model,@Value("${openai.enabled:false}") boolean enabled){this.apiKey=apiKey;this.model=model;this.enabled=enabled;}
 public String generateResponse(String systemPrompt,String userContextPrompt,String userMessage){if(!enabled||apiKey==null||apiKey.isBlank()){return generateFallbackResponse(userMessage);}try{var service=new OpenAiService(apiKey,Duration.ofSeconds(30));var messages=new ArrayList<ChatMessage>();messages.add(new ChatMessage("system",systemPrompt+"\n\n"+userContextPrompt));messages.add(new ChatMessage("user",userMessage));var request=ChatCompletionRequest.builder().model(model).messages(messages).temperature(0.7).maxTokens(500).build();var completion=service.createChatCompletion(request);return completion.getChoices().get(0).getMessage().getContent();}catch(Exception e){return generateFallbackResponse(userMessage);}}
 private String generateFallbackResponse(String userMessage){return "안녕하세요. 일상 웰니스를 지원하는 Morrow입니다.\n\n오늘 기분은 어떠신가요? 체크인을 통해 상태를 기록하고 개인화된 추천을 받아보세요.\n\n💡 팁: 규칙적인 체크인으로 자신의 패턴을 파악할 수 있습니다.";}
}
