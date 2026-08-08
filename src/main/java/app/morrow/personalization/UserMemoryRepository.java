package app.morrow.personalization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMemoryRepository extends JpaRepository<UserMemory, UUID> {
    Optional<UserMemory> findByUserIdAndMemoryKey(String userId, String memoryKey);
    List<UserMemory> findByUserIdAndActiveTrueOrderByConfidenceDescUpdatedAtDesc(String userId);
    List<UserMemory> findByUserIdOrderByUpdatedAtDesc(String userId);
    void deleteByUserId(String userId);
}
