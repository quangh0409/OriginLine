package vn.giapha.clan.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn thô bảng {@code clan_profile}. Bảng một dòng nên "dòng đầu tiên" là "dòng duy nhất". */
interface ClanProfileJpaRepository extends JpaRepository<ClanProfileJpaEntity, UUID> {

    Optional<ClanProfileJpaEntity> findFirstByOrderByCreatedAtAsc();
}
