package app.morrow.api;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1")
public class DashboardController {
 @GetMapping("/dashboard") Dashboard dashboard(){return new Dashboard("HIGHER_THAN_USUAL",68,new Metrics(348,72,41,4821),List.of(new Timeline("sleep","08:10","수면 회복이 평소보다 낮음","7일 평균보다 1시간 12분 적음","sleep",false),new Timeline("checkin","13:40","워치 상태 체크인","피로 · 원인: 수면 부족","checkin",true),new Timeline("recovery","15:20","10분 걷기 추천 완료","상태가 조금 나아짐으로 변경됨","recovery",true)),new Recommendation("walk-7","7분 동안 가볍게 걸어보세요","비슷한 피로 상태에서 짧은 걷기가 가장 자주 도움이 됐습니다."),"의료 진단이 아닌 일상 웰니스 분석입니다.");}
 record Metrics(int sleepMinutes,int restingHeartRate,int hrv,int steps){} record Timeline(String id,String time,String title,String detail,String kind,boolean userConfirmed){} record Recommendation(String id,String title,String rationale){} record Dashboard(String wellnessLoad,int score,Metrics metrics,List<Timeline> timeline,Recommendation recommendation,String disclaimer){}
}
