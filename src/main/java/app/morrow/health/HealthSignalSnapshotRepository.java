package app.morrow.health;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthSignalSnapshotRepository extends JpaRepository<HealthSignalSnapshot,UUID>{
 Optional<HealthSignalSnapshot> findByUserIdAndClientSnapshotId(String userId,String clientSnapshotId);
 List<HealthSignalSnapshot> findTop12ByUserIdOrderByRecordedAtDesc(String userId);
 Optional<HealthSignalSnapshot> findFirstByUserIdOrderByRecordedAtDesc(String userId);
 Optional<HealthSignalSnapshot> findFirstByUserIdAndSourceOrderByRecordedAtDesc(String userId,HealthSignalSnapshot.Source source);
 void deleteByUserId(String userId);
}
