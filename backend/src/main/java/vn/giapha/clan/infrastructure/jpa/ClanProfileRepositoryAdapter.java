package vn.giapha.clan.infrastructure.jpa;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.giapha.clan.domain.AncestralHall;
import vn.giapha.clan.domain.ClanProfile;
import vn.giapha.clan.domain.ClanProfileContent;
import vn.giapha.clan.domain.ClanRegion;
import vn.giapha.clan.domain.GioTo;
import vn.giapha.clan.domain.RootBranchLabel;
import vn.giapha.clan.domain.port.ClanProfileRepository;

/**
 * Hiện thực {@link ClanProfileRepository} trên JPA.
 *
 * <p><b>Đọc lại sau khi ghi</b> ({@code flush} + {@code refresh}): {@code created_at}/
 * {@code updated_at} do CSDL sinh (default + trigger {@code giapha_touch_updated_at}), và
 * {@code version} tăng lúc flush — không đọc lại thì phản hồi của {@code PUT} mang {@code ETag}
 * cũ, và lượt sửa kế tiếp của chính người vừa lưu sẽ nhận {@code 409} vô cớ.</p>
 */
@Repository
public class ClanProfileRepositoryAdapter implements ClanProfileRepository {

    private final ClanProfileJpaRepository jpa;
    private final EntityManager entityManager;

    public ClanProfileRepositoryAdapter(ClanProfileJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ClanProfile> find() {
        return jpa.findFirstByOrderByCreatedAtAsc().map(ClanProfileRepositoryAdapter::toDomain);
    }

    @Override
    public ClanProfile save(ClanProfile profile) {
        ClanProfileJpaEntity entity = jpa.findById(profile.id())
                .orElseGet(() -> new ClanProfileJpaEntity(profile.id()));
        ClanProfileContent c = profile.content();
        entity.setName(c.name());
        entity.setShortName(c.shortName());
        entity.setRootBranchPath(c.root().value());
        entity.setRegion(c.region().name());
        entity.setAncestralHallName(c.hall() == null ? null : c.hall().name());
        entity.setAncestralHallAddress(c.hall() == null ? null : c.hall().address());
        entity.setAncestorName(c.ancestorName());
        entity.setFoundedNote(c.foundedNote());
        entity.setGioToLunarDay(c.gioTo() == null ? null : (short) c.gioTo().lunarDay());
        entity.setGioToLunarMonth(c.gioTo() == null ? null : (short) c.gioTo().lunarMonth());
        ClanProfileJpaEntity saved = jpa.saveAndFlush(entity);
        entityManager.refresh(saved);
        return toDomain(saved);
    }

    private static ClanProfile toDomain(ClanProfileJpaEntity e) {
        AncestralHall hall = e.getAncestralHallName() == null ? null
                : new AncestralHall(e.getAncestralHallName(), e.getAncestralHallAddress());
        GioTo gioTo = e.getGioToLunarDay() == null || e.getGioToLunarMonth() == null ? null
                : new GioTo(e.getGioToLunarDay(), e.getGioToLunarMonth());
        ClanProfileContent content = new ClanProfileContent(e.getName(), e.getShortName(),
                RootBranchLabel.of(e.getRootBranchPath()), ClanRegion.parse(e.getRegion()), hall,
                e.getAncestorName(), e.getFoundedNote(), gioTo);
        return new ClanProfile(e.getId(), content, instant(e.getCreatedAt()),
                instant(e.getUpdatedAt()), e.getVersion());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
