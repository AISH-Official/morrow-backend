package app.morrow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {
    Optional<PushDevice> findByDeviceToken(String deviceToken);
    List<PushDevice> findByUserIdAndActiveTrue(String userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDevice d where d.userId = :userId and d.active = true")
    List<PushDevice> findActiveForDelivery(@Param("userId") String userId);
    @Query("select distinct d.userId from PushDevice d where d.active = true")
    List<String> findDistinctActiveUserIds();
    long countByActiveTrue();
    void deleteByUserId(String userId);
}
