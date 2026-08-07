package app.morrow.assistant;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
@Component
public class SafetyFilter {
 private static final List<String> CRISIS_KEYWORDS=Arrays.asList("자살","자해","죽고","끝내고","사라지고","목숨","생을","죽이");
 private static final List<String> MEDICAL_KEYWORDS=Arrays.asList("진단","약","처방","치료","병원","의사","약물","정신과","상담사");
 private static final String CRISIS_RESPONSE="지금 매우 어려운 상황이신 것 같습니다. 전문적인 도움이 필요합니다.\n\n📞 긴급 지원:\n- 정신건강 위기상담: 1577-0199 (24시간)\n- 생명의 전화: 1588-9191\n- 희망의 전화: 129 (보건복지콜센터)\n- 자살예방상담전화: 1393\n\n지금 주변의 믿을 수 있는 사람에게 연락하거나 가까운 병원 응급실을 방문해 주세요. 당신은 혼자가 아닙니다.";
 private static final String MEDICAL_RESPONSE="죄송합니다. 저는 의료 진단이나 치료를 제공할 수 없습니다.\n\n이 앱은 일상 웰니스 지원 도구이며 의료기기가 아닙니다. 증상이 지속되거나 걱정되는 경우 반드시 의료 전문가와 상담하시기 바랍니다.\n\n저는 일상적인 스트레스 관리, 수면 패턴, 활동 제안 등으로 도움을 드릴 수 있습니다.";
 public SafetyCheckResult check(String userMessage){if(containsCrisisKeywords(userMessage))return new SafetyCheckResult(true,CRISIS_RESPONSE,SafetyLevel.CRISIS);if(containsMedicalKeywords(userMessage))return new SafetyCheckResult(true,MEDICAL_RESPONSE,SafetyLevel.MEDICAL);return new SafetyCheckResult(false,null,SafetyLevel.SAFE);}
 private boolean containsCrisisKeywords(String content){return CRISIS_KEYWORDS.stream().anyMatch(content::contains);}
 private boolean containsMedicalKeywords(String content){return MEDICAL_KEYWORDS.stream().anyMatch(content::contains);}
 public record SafetyCheckResult(boolean blocked,String responseOverride,SafetyLevel level){}
 public enum SafetyLevel{SAFE,MEDICAL,CRISIS}
}
