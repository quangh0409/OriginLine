package vn.giapha.membership.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng {@code app_user}. */
public interface AppUserJpaRepository extends JpaRepository<AppUserJpaEntity, UUID> {

    Optional<AppUserJpaEntity> findByKeycloakSub(String keycloakSub);

    Optional<AppUserJpaEntity> findByPersonId(UUID personId);

    List<AppUserJpaEntity> findByPersonIdIn(Collection<UUID> personIds);
}
