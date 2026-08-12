package app.morrow.health;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;

@Service @Transactional
public class HealthSignalSnapshotService{
 private final HealthSignalSnapshotRepository repository;
 private final ApplicationEventPublisher events;
 private final ObjectMapper objectMapper;
 public HealthSignalSnapshotService(HealthSignalSnapshotRepository repository,ApplicationEventPublisher events,ObjectMapper objectMapper){this.repository=repository;this.events=events;this.objectMapper=objectMapper;}
 public HealthSignalSnapshot create(CreateSnapshot input){
  var userId=input.userId()==null||input.userId().isBlank()?"default-user":input.userId();
  var existing=repository.findByUserIdAndClientSnapshotId(userId,input.clientSnapshotId());
  var recordedAt=input.recordedAt()==null?OffsetDateTime.now():input.recordedAt();
  var sleepJson=writeJson(input.sleepSession());
  var workoutsJson=writeJson(input.workouts()==null?List.of():input.workouts());
  if(existing.isPresent()){
   var snapshot=existing.get();
   if(snapshot.refresh(input.source(),input.sleepMinutes(),input.heartRate(),input.restingHeartRate(),input.hrv(),input.steps(),input.activeEnergyKcal(),input.exerciseMinutes(),input.distanceMeters(),input.flightsClimbed(),input.respiratoryRate(),input.oxygenSaturationPercent(),recordedAt,sleepJson,workoutsJson))events.publishEvent(new HealthSnapshotCreatedEvent(snapshot));
   return snapshot;
  }
  var saved=repository.save(new HealthSignalSnapshot(userId,input.clientSnapshotId(),input.source(),input.sleepMinutes(),input.heartRate(),input.restingHeartRate(),input.hrv(),input.steps(),input.activeEnergyKcal(),input.exerciseMinutes(),input.distanceMeters(),input.flightsClimbed(),input.respiratoryRate(),input.oxygenSaturationPercent(),recordedAt,sleepJson,workoutsJson));
  events.publishEvent(new HealthSnapshotCreatedEvent(saved));
  return saved;
 }
 @Transactional(readOnly=true) public java.util.Optional<HealthSignalSnapshot> latest(String userId){return repository.findFirstByUserIdOrderByRecordedAtDesc(userId);}
 @Transactional(readOnly=true) public java.util.Optional<HealthSignalSnapshot> latest(String userId,HealthSignalSnapshot.Source source){return repository.findFirstByUserIdAndSourceOrderByRecordedAtDesc(userId,source);}
 @Transactional(readOnly=true) public java.util.List<HealthSignalSnapshot> recent(String userId){return repository.findTop12ByUserIdOrderByRecordedAtDesc(userId);}
 @Transactional(readOnly=true) public HealthDetails latestDetails(String userId){
  for(var snapshot:repository.findTop12ByUserIdOrderByRecordedAtDesc(userId)){
   var sleep=readSleep(snapshot.getSleepDetailJson());var workouts=readWorkouts(snapshot.getWorkoutDetailsJson());
   if(sleep!=null||!workouts.isEmpty())return new HealthDetails(sleep,workouts);
  }
  return new HealthDetails(null,List.of());
 }
 private String writeJson(Object value){if(value==null)return null;try{return objectMapper.writeValueAsString(value);}catch(Exception ignored){return null;}}
 private SleepSession readSleep(String json){if(json==null||json.isBlank())return null;try{return objectMapper.readValue(json,SleepSession.class);}catch(Exception ignored){return null;}}
 private List<WorkoutSession> readWorkouts(String json){if(json==null||json.isBlank())return List.of();try{return objectMapper.readValue(json,new TypeReference<List<WorkoutSession>>(){});}catch(Exception ignored){return List.of();}}
 public record CreateSnapshot(String userId,String clientSnapshotId,HealthSignalSnapshot.Source source,Integer sleepMinutes,Double heartRate,Double restingHeartRate,Double hrv,Double steps,Double activeEnergyKcal,Double exerciseMinutes,Double distanceMeters,Double flightsClimbed,Double respiratoryRate,Double oxygenSaturationPercent,OffsetDateTime recordedAt,SleepSession sleepSession,List<WorkoutSession> workouts){
  public CreateSnapshot(String userId,String clientSnapshotId,HealthSignalSnapshot.Source source,Integer sleepMinutes,Double heartRate,Double restingHeartRate,Double hrv,Double steps,Double activeEnergyKcal,Double exerciseMinutes,Double distanceMeters,Double flightsClimbed,Double respiratoryRate,Double oxygenSaturationPercent,OffsetDateTime recordedAt){this(userId,clientSnapshotId,source,sleepMinutes,heartRate,restingHeartRate,hrv,steps,activeEnergyKcal,exerciseMinutes,distanceMeters,flightsClimbed,respiratoryRate,oxygenSaturationPercent,recordedAt,null,List.of());}
 }
 public record SleepSession(String clientSleepId,OffsetDateTime startAt,OffsetDateTime endAt,int totalMinutes,int coreMinutes,int deepMinutes,int remMinutes,int awakeMinutes,String source){}
 public record WorkoutSession(String clientWorkoutId,String activityType,OffsetDateTime startAt,OffsetDateTime endAt,double durationMinutes,double activeEnergyKcal,double distanceMeters,double averageHeartRate,double maxHeartRate,String intensity,String source){}
 public record HealthDetails(SleepSession sleep,List<WorkoutSession> workouts){}
 public record HealthSnapshotCreatedEvent(HealthSignalSnapshot snapshot){}
}
