package app.morrow.checkin;

import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationService;
import app.morrow.personalization.PersonalizationService;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service @Transactional
public class CheckInService {
 private final CheckInRepository repository;
 private final TimelineService timelineService;
 private final RecommendationService recommendationService;
 private final PersonalizationService personalizationService;

 public CheckInService(CheckInRepository repository,TimelineService timelineService,RecommendationService recommendationService,PersonalizationService personalizationService){
  this.repository=repository;this.timelineService=timelineService;this.recommendationService=recommendationService;this.personalizationService=personalizationService;
 }

 public CheckIn create(CreateCheckIn input){
  var userId=input.userId()==null||input.userId().isBlank()?"default-user":input.userId();
  var recordedAt=input.recordedAt()==null?OffsetDateTime.now():input.recordedAt();
  var checkIn=repository.save(new CheckIn(userId,input.status(),input.cause(),input.note(),input.source(),recordedAt));
  var localTime=recordedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
  timelineService.create(new TimelineService.CreateTimeline(userId,localTime,"상태 체크인",timelineDetail(checkIn),Timeline.Kind.CHECKIN,true));
  var recommendation=recommendationFor(checkIn);
  var personalized=personalizationService.personalizeAction(userId,checkIn.getStatus(),recommendation.title(),recommendation.rationale());
  recommendationService.create(new RecommendationService.CreateRecommendation(userId,personalized.title(),personalized.rationale(),Recommendation.Status.ACTIVE));
  personalizationService.learnFromCheckIn(checkIn);
  return checkIn;
 }

 public void delete(UUID id){repository.deleteById(id);}
 public void deleteAll(String userId){repository.deleteByUserId(userId);}

 private String timelineDetail(CheckIn value){
  var cause=value.getCause()==null?"원인 미기록":causeLabel(value.getCause());
  var note=value.getNote()==null||value.getNote().isBlank()?"":" · "+value.getNote();
  return statusLabel(value.getStatus())+" · "+cause+note;
 }

 private SuggestedAction recommendationFor(CheckIn value){
  return switch(value.getStatus()){
   case OK->new SuggestedAction("좋은 흐름을 10분 더 이어가 보세요","현재의 안정적인 리듬을 가벼운 움직임으로 유지해 보세요.");
   case TENSE->new SuggestedAction("1분 동안 내쉬는 숨을 길게 해보세요","긴장이 기록된 순간에는 짧고 느린 호흡이 가장 부담이 적어요.");
   case TIRED->value.getCause()==CheckIn.Cause.SLEEP
    ?new SuggestedAction("7분 동안 가볍게 걸어보세요","수면이 부족한 피로에는 강한 운동보다 짧은 움직임이 부담이 적어요.")
    :new SuggestedAction("물 한 잔과 함께 5분 쉬어보세요","피로를 밀어붙이기보다 짧은 회복 구간을 먼저 만들어 보세요.");
   case LOW_FOCUS->new SuggestedAction("15분 집중 세션을 시작해 보세요","해야 할 일을 하나만 남기고 짧게 시작하면 진입 부담을 줄일 수 있어요.");
   case UNCOMFORTABLE->new SuggestedAction("자세를 바꾸고 몸을 천천히 살펴보세요","불편함이 지속되거나 심해지면 활동을 멈추고 전문가와 상의하세요.");
  };
 }

 private String statusLabel(CheckIn.Status value){return switch(value){case OK->"괜찮음";case TENSE->"긴장";case TIRED->"피로";case LOW_FOCUS->"집중 저하";case UNCOMFORTABLE->"불편함";};}
 private String causeLabel(CheckIn.Cause value){return switch(value){case SLEEP->"수면";case WORK->"업무";case STUDY->"학업";case RELATIONSHIP->"관계";case PHYSICAL->"신체";case UNKNOWN->"복합 요인";};}

 public record CreateCheckIn(String userId,CheckIn.Status status,CheckIn.Cause cause,String note,CheckIn.Source source,OffsetDateTime recordedAt){}
 private record SuggestedAction(String title,String rationale){}
}
