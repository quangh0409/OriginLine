package vn.giapha.membership.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập danh mục {@code role}. Chỉ đọc — năm dòng được seed trong V5. */
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByCode(String code);
}
