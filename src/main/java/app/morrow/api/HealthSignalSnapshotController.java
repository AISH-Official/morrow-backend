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

@RestController @RequestMapping("/api/v1/health/snapshots")
public class HealthSignalSnapshotController{
 private final HealthSignalSnapshotService service; private final RequestUserResolver users;
 public HealthSignalSnapshotController(HealthSignalSnapshotService service,RequestUserResolver users){this.service=service;this.users=users;}
 @PostMapping ResponseEntity<SnapshotResponse> create(@Valid @RequestBody CreateRequest request){
  var saved=service.create(new HealthSignalSnapshotService.CreateSnapshot(users.resolve(request.userId()),request.clientSnapshotId(),request.source(),request.sleepMinutes(),request.heartRate(),request.restingHeartRate(),request.hrv(),request.steps(),request.activeEnergyKcal(),request.exerciseMinutes(),request.distanceMeters(),request.flightsClimbed(),request.respiratoryRate(),request.oxygenSaturationPercent(),request.recordedAt()));
  return ResponseEntity.created(URI.create("/api/v1/health/snapshots/"+saved.getId())).body(SnapshotResponse.from(saved));
 }
 record CreateRequest(@Size(max=100)String userId,@NotBlank @Size(max=100)String clientSnapshotId,@NotNull HealthSignalSnapshot.Source source,@PositiveOrZero Integer sleepMinutes,@PositiveOrZero Double heartRate,@PositiveOrZero Double restingHeartRate,@PositiveOrZero Double hrv,@PositiveOrZero Double steps,@PositiveOrZero Double activeEnergyKcal,@PositiveOrZero Double exerciseMinutes,@PositiveOrZero Double distanceMeters,@PositiveOrZero Double flightsClimbed,@PositiveOrZero Double respiratoryRate,@PositiveOrZero Double oxygenSaturationPercent,OffsetDateTime recordedAt){}
 record SnapshotResponse(UUID id,String userId,String clientSnapshotId,String source,OffsetDateTime recordedAt){static SnapshotResponse from(HealthSignalSnapshot value){return new SnapshotResponse(value.getId(),value.getUserId(),value.getClientSnapshotId(),value.getSource().name(),value.getRecordedAt());}}
}
