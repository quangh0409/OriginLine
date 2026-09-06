package vn.giapha.genealogy.infrastructure.jpa;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.BranchKind;
import vn.giapha.genealogy.domain.Region;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.shared.vo.BranchPath;

/** Hiện thực {@link BranchRepository}. Truy vấn theo {@code ltree} là native (xem repository). */
@Repository
public class BranchRepositoryAdapter implements BranchRepository {

    private final BranchJpaRepository branches;

    public BranchRepositoryAdapter(BranchJpaRepository branches) {
        this.branches = branches;
    }

    @Override
    public Optional<Branch> byId(UUID id) {
        return id == null ? Optional.empty() : branches.findById(id).map(this::toDomain);
    }

    @Override
    public List<Branch> byIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return branches.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Branch> byPath(BranchPath path) {
        return path == null ? Optional.empty() : branches.findByPath(path.value()).map(this::toDomain);
    }

    @Override
    public List<Branch> subtreeOf(BranchPath path) {
        return path == null ? List.of() : branches.findSubtree(path.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Branch> all() {
        return branches.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Map<UUID, BranchPath> pathsOf(Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BranchPath> result = new HashMap<>();
        for (BranchJpaEntity entity : branches.findByIdIn(branchIds)) {
            result.put(entity.getId(), BranchPath.of(entity.getPath()));
        }
        return result;
    }

    private Branch toDomain(BranchJpaEntity entity) {
        return Branch.rehydrate(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .path(BranchPath.of(entity.getPath()))
                .parentId(entity.getParentId())
                .kind(BranchKind.valueOf(entity.getBranchKind()))
                .region(entity.getRegion() == null ? null : Region.valueOf(entity.getRegion()))
                .headPersonId(entity.getHeadPersonId())
                .foundedYear(entity.getFoundedYear())
                .note(entity.getNote())
                .sortOrder(entity.getSortOrder())
                .deleted(entity.isDeleted())
                .version(entity.getVersion())
                .build();
    }
}
