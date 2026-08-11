package app.morrow.assistant;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
@Component
public class SafetyFilter {
 private static final List<String> CRISIS_KEYWORDS=Arrays.asList("자살","자해","죽고","죽고싶","살기 싫","끝내고","사라지고","목숨","생을","죽이","버티기 힘","suicide","kill myself","self harm","end my life");
 private static final List<String> MEDICAL_KEYWORDS=Arrays.asList("진단","처방","치료","병원","의사","약물","정신과","약을 먹","약을 끊","복용");
 private static final String CRISIS_RESPONSE="지금 혼자 버티지 않아도 괜찮아요. 즉시 위험하다면 119 또는 112에 연락하고, 자살예방상담전화 109에서 24시간 도움을 받을 수 있어요. 가능하면 지금 믿을 수 있는 사람에게도 곁에 있어 달라고 알려주세요.";
 private static final String MEDICAL_RESPONSE="죄송합니다. 저는 의료 진단이나 치료를 제공할 수 없습니다.\n\n이 앱은 일상 웰니스 지원 도구이며 의료기기가 아닙니다. 증상이 지속되거나 걱정되는 경우 반드시 의료 전문가와 상담하시기 바랍니다.\n\n저는 일상적인 스트레스 관리, 수면 패턴, 활동 제안 등으로 도움을 드릴 수 있습니다.";
 public SafetyCheckResult check(String userMessage){var normalized=userMessage==null?"":userMessage.toLowerCase();if(containsCrisisKeywords(normalized))return new SafetyCheckResult(true,CRISIS_RESPONSE,SafetyLevel.CRISIS);if(containsMedicalKeywords(normalized))return new SafetyCheckResult(true,MEDICAL_RESPONSE,SafetyLevel.MEDICAL);return new SafetyCheckResult(false,null,SafetyLevel.SAFE);}
 private boolean containsCrisisKeywords(String content){return CRISIS_KEYWORDS.stream().anyMatch(content::contains);}
 private boolean containsMedicalKeywords(String content){return MEDICAL_KEYWORDS.stream().anyMatch(content::contains);}
 public record SafetyCheckResult(boolean blocked,String responseOverride,SafetyLevel level){}
 public enum SafetyLevel{SAFE,MEDICAL,CRISIS}
}
