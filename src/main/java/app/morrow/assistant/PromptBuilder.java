package app.morrow.assistant;
import app.morrow.checkin.CheckIn;
import app.morrow.recommendation.Recommendation;
import app.morrow.timeline.Timeline;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
@Component
public class PromptBuilder {
 private static final String SYSTEM_PROMPT="당신은 일상 웰니스를 지원하는 AI 어시스턴트입니다.\n\n핵심 원칙:\n- 의료 진단이나 치료를 제공하지 않습니다\n- 생체 데이터만으로 질환을 확정하지 않습니다\n- 약물 변경이나 치료 중단을 지시하지 않습니다\n- 위기 상황에서는 전문 지원을 최우선으로 안내합니다\n- 사용자의 과거 기록을 참고해 개인화된 조언을 제공합니다\n- 공감하고 지지하는 톤으로 대화합니다\n\n사용자의 데이터를 학습하여 그들의 패턴, 효과적인 회복 방법, 어려움을 겪는 시간대를 파악하고 맞춤형 조언을 제공하세요.";
 public String buildSystemPrompt(){return SYSTEM_PROMPT;}
 public String buildUserContextPrompt(UserContextCollector.UserContext context){var sb=new StringBuilder();sb.append("=== 사용자 컨텍스트 ===\n\n");if(!context.recentCheckIns().isEmpty()){sb.append("최근 7일 체크인 기록:\n");context.recentCheckIns().stream().limit(10).forEach(c->sb.append(String.format("- %s: %s 상태, 원인: %s%s\n",c.getRecordedAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),translateStatus(c.getStatus()),c.getCause()!=null?translateCause(c.getCause()):"미기록",c.getNote()!=null?" ("+c.getNote()+")":"")));}if(!context.recentTimelines().isEmpty()){sb.append("\n최근 타임라인:\n");context.recentTimelines().stream().limit(5).forEach(t->sb.append(String.format("- %s: %s - %s\n",t.getTime(),t.getTitle(),t.getDetail())));}if(!context.recentRecommendations().isEmpty()){sb.append("\n최근 추천:\n");context.recentRecommendations().stream().limit(3).forEach(r->sb.append(String.format("- %s (상태: %s)\n  근거: %s\n",r.getTitle(),r.getStatus(),r.getRationale())));}if(!context.recentMessages().isEmpty()){sb.append("\n최근 대화 (24시간):\n");context.recentMessages().stream().limit(6).forEach(m->sb.append(String.format("- %s: %s\n",m.getRole()==AssistantMessage.Role.USER?"사용자":"AI",m.getContent().length()>100?m.getContent().substring(0,100)+"...":m.getContent())));}if(context.recentCheckIns().isEmpty()&&context.recentTimelines().isEmpty()&&context.recentRecommendations().isEmpty()){sb.append("아직 기록된 데이터가 없습니다. 체크인을 시작해보세요!\n");}return sb.toString();}
 private String translateStatus(CheckIn.Status status){return switch(status){case OK->"양호";case TENSE->"긴장";case TIRED->"피로";case LOW_FOCUS->"집중력 저하";case UNCOMFORTABLE->"불편";};}
 private String translateCause(CheckIn.Cause cause){return switch(cause){case SLEEP->"수면";case WORK->"업무";case STUDY->"학업";case RELATIONSHIP->"인간관계";case PHYSICAL->"신체";case UNKNOWN->"알 수 없음";};}
}
