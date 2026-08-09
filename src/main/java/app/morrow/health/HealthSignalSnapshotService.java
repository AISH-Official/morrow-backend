package app.morrow.health;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.time.OffsetDateTime;

@Service @Transactional
public class HealthSignalSnapshotService{
 private final HealthSignalSnapshotRepository repository;
 private final ApplicationEventPublisher events;
 public HealthSignalSnapshotService(HealthSignalSnapshotRepository repository,ApplicationEventPublisher events){this.repository=repository;this.events=events;}
 public HealthSignalSnapshot create(CreateSnapshot input){
  var userId=input.userId()==null||input.userId().isBlank()?"default-user":input.userId();
  var existing=repository.findByUserIdAndClientSnapshotId(userId,input.clientSnapshotId());
  if(existing.isPresent())return existing.get();
  var saved=repository.save(new HealthSignalSnapshot(userId,input.clientSnapshotId(),input.source(),input.sleepMinutes(),input.heartRate(),input.restingHeartRate(),input.hrv(),input.steps(),input.activeEnergyKcal(),input.exerciseMinutes(),input.distanceMeters(),input.flightsClimbed(),input.respiratoryRate(),input.oxygenSaturationPercent(),input.recordedAt()==null?OffsetDateTime.now():input.recordedAt()));
  events.publishEvent(new HealthSnapshotCreatedEvent(saved));
  return saved;
 }
 @Transactional(readOnly=true) public java.util.Optional<HealthSignalSnapshot> latest(String userId){return repository.findFirstByUserIdOrderByRecordedAtDesc(userId);}
 @Transactional(readOnly=true) public java.util.Optional<HealthSignalSnapshot> latest(String userId,HealthSignalSnapshot.Source source){return repository.findFirstByUserIdAndSourceOrderByRecordedAtDesc(userId,source);}
 public record CreateSnapshot(String userId,String clientSnapshotId,HealthSignalSnapshot.Source source,Integer sleepMinutes,Double heartRate,Double restingHeartRate,Double hrv,Double steps,Double activeEnergyKcal,Double exerciseMinutes,Double distanceMeters,Double flightsClimbed,Double respiratoryRate,Double oxygenSaturationPercent,OffsetDateTime recordedAt){}
 public record HealthSnapshotCreatedEvent(HealthSignalSnapshot snapshot){}
}
