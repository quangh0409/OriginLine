package vn.giapha.kinship.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn thô bảng {@code kinship_rule_set}. Việc chọn bộ luật nào nằm ở {@link RuleSetJpaRepository}. */
interface KinshipRuleSetJpaRepository extends JpaRepository<KinshipRuleSetEntity, UUID> {

    Optional<KinshipRuleSetEntity> findByCode(String code);

    /**
     * Bộ mặc định của hệ thống. V3 có unique index bảo đảm chỉ một bộ {@code DEFAULT} đang hoạt
     * động, nên truy vấn này không thể trả nhiều dòng.
     */
    Optional<KinshipRuleSetEntity> findFirstByScopeAndActiveIsTrue(String scope);

    Optional<KinshipRuleSetEntity> findFirstByScopeAndRegionAndActiveIsTrue(String scope, String region);

    /**
     * Bộ luật của một chi ở một cấp cụ thể. Dùng {@code IN} trên danh sách tổ tiên ltree để một
     * lượt đi CSDL trả về mọi ứng viên, thay vì hỏi từng đời một khi leo cây chi.
     */
    @Query("""
            select s from KinshipRuleSetEntity s
            where s.scope = :scope and s.branchId in :branchIds and s.active = true
            """)
    List<KinshipRuleSetEntity> findByScopeAndBranchIds(@Param("scope") String scope,
            @Param("branchIds") List<UUID> branchIds);

    /** Liệt kê quản trị. Tham số {@code null} nghĩa là không lọc theo chiều đó. */
    @Query("""
            select s from KinshipRuleSetEntity s
            where (:scope    is null or s.scope    = :scope)
              and (:region   is null or s.region   = :region)
              and (:branchId is null or s.branchId = :branchId)
            order by s.scope, s.code
            """)
    List<KinshipRuleSetEntity> search(@Param("scope") String scope, @Param("region") String region,
            @Param("branchId") UUID branchId);
}
