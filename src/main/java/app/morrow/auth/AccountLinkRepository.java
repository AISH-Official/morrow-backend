package app.morrow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountLinkRepository extends JpaRepository<AccountLink, String> {
    Optional<AccountLink> findByUserId(String userId);
    void deleteByUserId(String userId);
}
