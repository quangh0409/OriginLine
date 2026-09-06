package vn.giapha.membership.infrastructure.jpa;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập {@code branch_assignment}.
 *
 * <p>Ba truy vấn "đã phân giải" trả thẳng {@code role.code} và {@code branch.path} kèm theo. Ép hai
 * phép nối ấy vào một câu duy nhất là có chủ ý: mọi request có kiểm quyền đều chạy qua đây, và một
 * vòng lặp Java tra thêm {@code role} rồi {@code branch} cho từng dòng sẽ biến chính lớp bảo vệ
 * thành nút thắt.</p>
 *
 * <p>Điều kiện hiệu lực ({@code valid_from}/{@code valid_to}) nằm trong SQL, không ở Java: một
 * nhiệm kỳ đã hết mà vẫn được nạp lên rồi mới lọc là thêm một cơ hội để ai đó quên lọc.</p>
 */
public interface BranchAssignmentJpaRepository extends JpaRepository<BranchAssignmentJpaEntity, UUID> {

    String SELECT_RESOLVED = """
            SELECT ba.id            AS id,
                   ba.app_user_id   AS app_user_id,
                   r.code           AS role_code,
                   ba.branch_id     AS branch_id,
                   b.path::text     AS branch_path,
                   ba.valid_from    AS valid_from,
                   ba.valid_to      AS valid_to,
                   ba.granted_by    AS granted_by,
                   ba.note          AS note
              FROM branch_assignment ba
              JOIN role r ON r.id = ba.role_id
              LEFT JOIN branch b ON b.id = ba.branch_id AND b.is_deleted = FALSE
            """;

    @Query(value = SELECT_RESOLVED + """
             WHERE ba.app_user_id = :appUserId
               AND (ba.valid_from IS NULL OR ba.valid_from <= :on)
               AND (ba.valid_to   IS NULL OR ba.valid_to   >= :on)
             ORDER BY r.rank DESC, b.path NULLS FIRST
            """, nativeQuery = true)
    List<Object[]> findActiveFor(@Param("appUserId") UUID appUserId, @Param("on") LocalDate on);

    @Query(value = SELECT_RESOLVED + """
             WHERE ba.app_user_id = :appUserId
             ORDER BY r.rank DESC, b.path NULLS FIRST
            """, nativeQuery = true)
    List<Object[]> findAllFor(@Param("appUserId") UUID appUserId);

    @Query(value = SELECT_RESOLVED + " WHERE ba.id = :id", nativeQuery = true)
    List<Object[]> findResolvedById(@Param("id") UUID id);

    @Query(value = SELECT_RESOLVED + """
             WHERE r.code = :roleCode
               AND (CAST(:branchId AS uuid) IS NULL OR ba.branch_id = CAST(:branchId AS uuid))
               AND (ba.valid_from IS NULL OR ba.valid_from <= :on)
               AND (ba.valid_to   IS NULL OR ba.valid_to   >= :on)
             ORDER BY b.path NULLS FIRST
            """, nativeQuery = true)
    List<Object[]> findByRoleAndBranch(@Param("roleCode") String roleCode,
                                       @Param("branchId") UUID branchId,
                                       @Param("on") LocalDate on);
}
