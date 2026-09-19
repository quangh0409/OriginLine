package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập {@code invitation}.
 *
 * <p>Lọc phạm vi nằm trong SQL bằng {@code ltree[] @> ltree}, đúng khuôn mẫu đã dùng ở
 * {@code ChangeRequestJpaRepository} — đọc javadoc lớp ấy để biết vì sao {@code :paths} là một
 * chuỗi và {@code :clanWide} là {@code int}.</p>
 *
 * <p><b>Không có phương thức nào nhận mã thô.</b> Tra cứu chỉ đi qua băm; một chữ ký
 * {@code findByCode(String)} ở đây sớm muộn sẽ dẫn tới một câu log in ra tham số của nó, và mã mời
 * là bí mật duy nhất của cả luồng.</p>
 */
public interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, UUID> {

    Optional<InvitationJpaEntity> findByCodeHash(String codeHash);

    /**
     * Lời mời <b>đang mở</b> của một nhân khẩu — {@code ux_invitation_open_person} bảo đảm nhiều
     * nhất một dòng. Cố ý không xét hạn: phát lại phải thu hồi cả mã quá hạn, nếu không chỉ mục
     * duy nhất sẽ chặn lệnh chèn.
     */
    Optional<InvitationJpaEntity> findByPersonIdAndStatus(UUID personId, String status);

    @Query(value = """
            SELECT i.* FROM invitation i
              LEFT JOIN branch b ON b.id = i.branch_id AND b.is_deleted = FALSE
             WHERE (:clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
             ORDER BY i.created_at DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<InvitationJpaEntity> findInScope(@Param("paths") String paths,
                                          @Param("clanWide") int clanWide,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);
}
