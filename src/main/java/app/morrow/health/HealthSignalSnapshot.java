package app.morrow.health;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="health_signal_snapshots",uniqueConstraints=@UniqueConstraint(name="uk_health_snapshot_client",columnNames={"user_id","client_snapshot_id"}))
public class HealthSignalSnapshot {
 @Id private UUID id;
 @Column(name="user_id",nullable=false) private String userId;
 @Column(name="client_snapshot_id",nullable=false,length=100) private String clientSnapshotId;
 @Enumerated(EnumType.STRING) private Source source;
 private Integer sleepMinutes; private Double heartRate; private Double restingHeartRate; private Double hrv; private Double steps;
 private Double activeEnergyKcal; private Double exerciseMinutes; private Double distanceMeters; private Double flightsClimbed;
 private Double respiratoryRate; private Double oxygenSaturationPercent; private OffsetDateTime recordedAt;
 protected HealthSignalSnapshot(){}
 public HealthSignalSnapshot(String userId,String clientSnapshotId,Source source,Integer sleepMinutes,Double heartRate,Double restingHeartRate,Double hrv,Double steps,Double activeEnergyKcal,Double exerciseMinutes,Double distanceMeters,Double flightsClimbed,Double respiratoryRate,Double oxygenSaturationPercent,OffsetDateTime recordedAt){
  this.id=UUID.randomUUID();this.userId=userId;this.clientSnapshotId=clientSnapshotId;this.source=source;this.sleepMinutes=sleepMinutes;this.heartRate=heartRate;this.restingHeartRate=restingHeartRate;this.hrv=hrv;this.steps=steps;this.activeEnergyKcal=activeEnergyKcal;this.exerciseMinutes=exerciseMinutes;this.distanceMeters=distanceMeters;this.flightsClimbed=flightsClimbed;this.respiratoryRate=respiratoryRate;this.oxygenSaturationPercent=oxygenSaturationPercent;this.recordedAt=recordedAt;
 }
 public UUID getId(){return id;} public String getUserId(){return userId;} public String getClientSnapshotId(){return clientSnapshotId;} public Source getSource(){return source;} public Integer getSleepMinutes(){return sleepMinutes;} public Double getHeartRate(){return heartRate;} public Double getRestingHeartRate(){return restingHeartRate;} public Double getHrv(){return hrv;} public Double getSteps(){return steps;} public Double getActiveEnergyKcal(){return activeEnergyKcal;} public Double getExerciseMinutes(){return exerciseMinutes;} public Double getDistanceMeters(){return distanceMeters;} public Double getFlightsClimbed(){return flightsClimbed;} public Double getRespiratoryRate(){return respiratoryRate;} public Double getOxygenSaturationPercent(){return oxygenSaturationPercent;} public OffsetDateTime getRecordedAt(){return recordedAt;}
 public enum Source{IPHONE,WATCH}
}
