package app.morrow.config;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.checkin.CheckInService;
import app.morrow.timeline.Timeline;
import app.morrow.timeline.TimelineService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.ArrayList;

@Component
@ConditionalOnProperty(name="morrow.demo.seed",havingValue="true",matchIfMissing=true)
public class DemoDataInitializer implements ApplicationRunner {
 private static final String USER_ID="default-user";
 private final CheckInRepository repository;
 private final CheckInService checkInService;
 private final TimelineService timelineService;

 public DemoDataInitializer(CheckInRepository repository,CheckInService checkInService,TimelineService timelineService){
  this.repository=repository;this.checkInService=checkInService;this.timelineService=timelineService;
 }

 @Override public void run(ApplicationArguments args){
  if(!repository.findByUserIdAndRecordedAtAfterOrderByRecordedAtDesc(USER_ID,OffsetDateTime.now().minusYears(10)).isEmpty())return;
  var now=OffsetDateTime.now();
  var history=new ArrayList<CheckIn>();
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.WORK,"오전 집중이 잘 됐음",CheckIn.Source.IPHONE,now.minusDays(6)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.TIRED,CheckIn.Cause.SLEEP,"늦게 잠듦",CheckIn.Source.WATCH,now.minusDays(5)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.PHYSICAL,"산책 후 나아짐",CheckIn.Source.IPHONE,now.minusDays(5).plusHours(4)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.LOW_FOCUS,CheckIn.Cause.WORK,"회의가 길었음",CheckIn.Source.WEB,now.minusDays(4)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.WORK,"15분 집중 세션 완료",CheckIn.Source.IPHONE,now.minusDays(4).plusHours(3)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.TENSE,CheckIn.Cause.STUDY,"발표 준비",CheckIn.Source.WATCH,now.minusDays(3)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.PHYSICAL,"호흡 후 안정됨",CheckIn.Source.IPHONE,now.minusDays(3).plusHours(2)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.TIRED,CheckIn.Cause.SLEEP,"수면 6시간 미만",CheckIn.Source.WATCH,now.minusDays(2)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.PHYSICAL,"7분 걷기 완료",CheckIn.Source.IPHONE,now.minusDays(2).plusHours(2)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.OK,CheckIn.Cause.UNKNOWN,"평온한 하루",CheckIn.Source.IPHONE,now.minusDays(1)));
  history.add(new CheckIn(USER_ID,CheckIn.Status.TIRED,CheckIn.Cause.SLEEP,"아침부터 무거움",CheckIn.Source.WATCH,now.minusHours(5)));
  repository.saveAll(history);

  timelineService.create(new TimelineService.CreateTimeline(USER_ID,now.minusHours(9).toLocalTime(),"수면 회복이 평소보다 낮음","최근 7일 평균보다 1시간 12분 짧았어요.",Timeline.Kind.SLEEP,false));
  checkInService.create(new CheckInService.CreateCheckIn(USER_ID,CheckIn.Status.TIRED,CheckIn.Cause.SLEEP,"오후 집중력이 떨어짐",CheckIn.Source.WATCH,now.minusHours(3)));
  timelineService.create(new TimelineService.CreateTimeline(USER_ID,now.minusHours(1).toLocalTime(),"짧은 걷기로 회복","7분 걷기 후 상태가 조금 나아졌어요.",Timeline.Kind.RECOVERY,true));
 }
}
