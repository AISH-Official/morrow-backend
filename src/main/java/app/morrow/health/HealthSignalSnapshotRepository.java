package app.morrow.health;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface HealthSignalSnapshotRepository extends JpaRepository<HealthSignalSnapshot,UUID>{
 Optional<HealthSignalSnapshot> findByUserIdAndClientSnapshotId(String userId,String clientSnapshotId);
 Optional<HealthSignalSnapshot> findFirstByUserIdOrderByRecordedAtDesc(String userId);
 Optional<HealthSignalSnapshot> findFirstByUserIdAndSourceOrderByRecordedAtDesc(String userId,HealthSignalSnapshot.Source source);
 void deleteByUserId(String userId);
}
