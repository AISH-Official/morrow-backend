package app.morrow.api;

import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotService;
import app.morrow.auth.RequestUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

@RestController @RequestMapping("/api/v1/health/snapshots")
public class HealthSignalSnapshotController{
 private final HealthSignalSnapshotService service; private final RequestUserResolver users;
 public HealthSignalSnapshotController(HealthSignalSnapshotService service,RequestUserResolver users){this.service=service;this.users=users;}
 @PostMapping ResponseEntity<SnapshotResponse> create(@Valid @RequestBody CreateRequest request){
  var sleep=request.sleepSession()==null?null:request.sleepSession().toService();
  var workouts=request.workouts()==null?List.<HealthSignalSnapshotService.WorkoutSession>of():request.workouts().stream().map(WorkoutRequest::toService).toList();
  var saved=service.create(new HealthSignalSnapshotService.CreateSnapshot(users.resolve(request.userId()),request.clientSnapshotId(),request.source(),request.sleepMinutes(),request.heartRate(),request.restingHeartRate(),request.hrv(),request.steps(),request.activeEnergyKcal(),request.exerciseMinutes(),request.distanceMeters(),request.flightsClimbed(),request.respiratoryRate(),request.oxygenSaturationPercent(),request.recordedAt(),sleep,workouts));
  return ResponseEntity.created(URI.create("/api/v1/health/snapshots/"+saved.getId())).body(SnapshotResponse.from(saved));
 }
 record CreateRequest(@Size(max=100)String userId,@NotBlank @Size(max=100)String clientSnapshotId,@NotNull HealthSignalSnapshot.Source source,@PositiveOrZero Integer sleepMinutes,@PositiveOrZero Double heartRate,@PositiveOrZero Double restingHeartRate,@PositiveOrZero Double hrv,@PositiveOrZero Double steps,@PositiveOrZero Double activeEnergyKcal,@PositiveOrZero Double exerciseMinutes,@PositiveOrZero Double distanceMeters,@PositiveOrZero Double flightsClimbed,@PositiveOrZero Double respiratoryRate,@PositiveOrZero Double oxygenSaturationPercent,OffsetDateTime recordedAt,@Valid SleepRequest sleepSession,@Valid @Size(max=12)List<WorkoutRequest> workouts){}
 record SleepRequest(@NotBlank @Size(max=120)String clientSleepId,@NotNull OffsetDateTime startAt,@NotNull OffsetDateTime endAt,@PositiveOrZero int totalMinutes,@PositiveOrZero int coreMinutes,@PositiveOrZero int deepMinutes,@PositiveOrZero int remMinutes,@PositiveOrZero int awakeMinutes,@NotBlank @Size(max=20)String source){HealthSignalSnapshotService.SleepSession toService(){return new HealthSignalSnapshotService.SleepSession(clientSleepId,startAt,endAt,totalMinutes,coreMinutes,deepMinutes,remMinutes,awakeMinutes,source);}}
 record WorkoutRequest(@NotBlank @Size(max=120)String clientWorkoutId,@NotBlank @Size(max=80)String activityType,@NotNull OffsetDateTime startAt,@NotNull OffsetDateTime endAt,@PositiveOrZero double durationMinutes,@PositiveOrZero double activeEnergyKcal,@PositiveOrZero double distanceMeters,@PositiveOrZero double averageHeartRate,@PositiveOrZero double maxHeartRate,@NotBlank @Size(max=20)String intensity,@NotBlank @Size(max=20)String source){HealthSignalSnapshotService.WorkoutSession toService(){return new HealthSignalSnapshotService.WorkoutSession(clientWorkoutId,activityType,startAt,endAt,durationMinutes,activeEnergyKcal,distanceMeters,averageHeartRate,maxHeartRate,intensity,source);}}
 record SnapshotResponse(UUID id,String userId,String clientSnapshotId,String source,OffsetDateTime recordedAt){static SnapshotResponse from(HealthSignalSnapshot value){return new SnapshotResponse(value.getId(),value.getUserId(),value.getClientSnapshotId(),value.getSource().name(),value.getRecordedAt());}}
}
