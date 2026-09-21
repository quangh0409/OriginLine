package vn.giapha.content.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Honour;
import vn.giapha.content.domain.HonourKind;
import vn.giapha.content.domain.port.HonourRepository;
import vn.giapha.shared.vo.BranchPath;

/** Hiện thực {@link HonourRepository} trên JPA + native query {@code ltree}. */
@Repository
public class HonourRepositoryAdapter implements HonourRepository {

    private final HonourJpaRepository jpa;

    public HonourRepositoryAdapter(HonourJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Honour> byId(UUID id) {
        return id == null
                ? Optional.empty()
                : jpa.findById(id).map(HonourRepositoryAdapter::toDomain);
    }

    @Override
    public Honour save(Honour honour) {
        HonourJpaEntity entity = jpa.findById(honour.id())
                .orElseGet(() -> new HonourJpaEntity(honour.id(), honour.personId(),
                        honour.createdBy()));
        entity.setKind(honour.kind().name());
        entity.setTitle(honour.title());
        entity.setYear(honour.year());
        entity.setIssuer(honour.issuer());
        entity.setDescription(honour.description());
        entity.setStatus(honour.status().name());
        entity.setReviewedBy(honour.reviewedBy());
        entity.setReviewedAt(honour.reviewedAt());
        entity.setRejectReason(honour.rejectReason());
        entity.setDeleted(honour.isDeleted());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public List<Honour> search(UUID personId, HonourKind kind, UUID branchId, ContentStatus status,
                               UUID callerUserId, List<BranchPath> scopes, boolean clanWide,
                               int limit, int offset) {
        return jpa.findVisible(text(personId), kind == null ? null : kind.name(), text(branchId),
                        status == null ? null : status.name(), callerUserId,
                        ScopeLiteral.of(scopes), clanWide ? 1 : 0, limit, offset).stream()
                .map(HonourRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public long count(UUID personId, HonourKind kind, UUID branchId, ContentStatus status,
                      UUID callerUserId, List<BranchPath> scopes, boolean clanWide) {
        return jpa.countVisible(text(personId), kind == null ? null : kind.name(), text(branchId),
                status == null ? null : status.name(), callerUserId,
                ScopeLiteral.of(scopes), clanWide ? 1 : 0);
    }

    @Override
    public long countPendingInScope(List<BranchPath> scopes, boolean clanWide) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            return 0L;
        }
        return jpa.countPendingInScope(ScopeLiteral.of(scopes), clanWide ? 1 : 0);
    }

    /**
     * UUID lọc đi dưới dạng chuỗi rồi {@code CAST(... AS uuid)} trong SQL.
     *
     * <p>Postgres không suy được kiểu của một tham số đứng cạnh {@code IS NULL}, và thông báo lỗi
     * ("could not determine data type of parameter") không chỉ về chỗ sai. Đây là cùng lối mà
     * {@code ChangeRequestJpaRepository} dùng cho tham số trạng thái.</p>
     */
    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }

    private static Honour toDomain(HonourJpaEntity entity) {
        return new Honour(entity.getId(), entity.getPersonId(),
                HonourKind.of(entity.getKind()), entity.getTitle(), entity.getYear(),
                entity.getIssuer(), entity.getDescription(),
                ContentStatus.of(entity.getStatus()), entity.getCreatedBy(),
                entity.getReviewedBy(), entity.getReviewedAt(), entity.getRejectReason(),
                entity.isDeleted(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }
}
