package vn.giapha.genealogy.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng {@code branch}. Các truy vấn theo {@code ltree} phải là native. */
public interface BranchJpaRepository extends JpaRepository<BranchJpaEntity, UUID> {

    List<BranchJpaEntity> findByIdIn(Collection<UUID> ids);

    @Query(value = "SELECT * FROM branch WHERE path = CAST(:path AS ltree)", nativeQuery = true)
    Optional<BranchJpaEntity> findByPath(@Param("path") String path);

    /**
     * Cây con của một chi. Toán tử {@code <@} của ltree đọc là "nằm dưới hoặc chính là", và nó
     * dùng được chỉ mục GiST {@code ix_branch_path_gist} — khác hẳn một {@code LIKE 'x.%'} phải
     * quét bảng.
     */
    @Query(value = "SELECT * FROM branch WHERE path <@ CAST(:path AS ltree) ORDER BY path",
            nativeQuery = true)
    List<BranchJpaEntity> findSubtree(@Param("path") String path);
}
