package app.morrow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {
    Optional<PushDevice> findByDeviceToken(String deviceToken);
    List<PushDevice> findByUserIdAndActiveTrue(String userId);
    long countByActiveTrue();
    void deleteByUserId(String userId);
}
