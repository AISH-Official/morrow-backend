package app.morrow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {
    Optional<DeviceSession> findByDeviceId(String deviceId);
    Optional<DeviceSession> findByTokenHash(String tokenHash);
    Optional<DeviceSession> findByPairingCode(String pairingCode);
    void deleteByUserId(String userId);
}
