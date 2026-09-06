package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập {@code change_request}.
 *
 * <h2>Lọc phạm vi nằm trong SQL, bằng {@code ltree}</h2>
 * Toán tử {@code ltree[] @> ltree} của phần mở rộng {@code ltree} đọc là "mảng này có chứa một tổ
 * tiên của path kia không" — đúng nghĩa "chi được giao phủ chi đích". Dùng dạng toán tử một-lần này
 * thay cho {@code path <@ ANY(...)} vì {@code ltree} định nghĩa cả toán tử giữa {@code ltree} và
 * {@code ltree[]}, nên cấu trúc {@code ANY} có thêm một bước phân giải toán tử không cần thiết.
 *
 * <p>Quan trọng hơn cả tốc độ: lọc ở CSDL nghĩa là hàng đợi của chi khác <b>không bao giờ</b> đi
 * vào bộ nhớ tiến trình, nên không có chỗ nào để quên lọc.
 *
 * <h2>Vì sao {@code :paths} là một chuỗi</h2>
 * Đó là literal mảng của Postgres ({@code {goc.chi_giap,goc.chi_at}}) rồi ép sang {@code ltree[]}.
 * Truyền mảng Java qua native query của Hibernate cho ra hành vi khác nhau giữa các phiên bản
 * driver; một chuỗi thì không. An toàn tiêm lệnh được bảo đảm ở tầng trên: mọi nhãn đã qua
 * {@code BranchPath}, vốn chỉ nhận {@code [A-Za-z0-9_.]}.
 *
 * <h2>{@code :clanWide} là {@code int}, không phải {@code boolean}</h2>
 * Cùng lý do: ánh xạ boolean trong native query phụ thuộc phương ngữ. {@code 1} hoặc {@code 0} thì
 * không phụ thuộc gì cả.
 */
public interface ChangeRequestJpaRepository extends JpaRepository<ChangeRequestJpaEntity, UUID> {

    String PENDING_IN_SCOPE = """
              FROM change_request cr
              LEFT JOIN branch b ON b.id = cr.target_branch_id AND b.is_deleted = FALSE
             WHERE cr.status = 'PENDING'
               AND (:clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
            """;

    @Query(value = "SELECT cr.* " + PENDING_IN_SCOPE
            + " ORDER BY cr.created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<ChangeRequestJpaEntity> findPendingInScope(@Param("paths") String paths,
                                                    @Param("clanWide") int clanWide,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + PENDING_IN_SCOPE, nativeQuery = true)
    long countPendingInScope(@Param("paths") String paths, @Param("clanWide") int clanWide);

    @Query(value = """
            SELECT cr.* FROM change_request cr
             WHERE cr.requested_by = :appUserId
             ORDER BY cr.created_at DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ChangeRequestJpaEntity> findByRequester(@Param("appUserId") UUID appUserId,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    @Query(value = """
            SELECT cr.* FROM change_request cr
             WHERE cr.person_id = :personId
               AND (CAST(:status AS varchar) IS NULL OR cr.status = CAST(:status AS varchar))
             ORDER BY cr.created_at DESC
            """, nativeQuery = true)
    List<ChangeRequestJpaEntity> findByPerson(@Param("personId") UUID personId,
                                               @Param("status") String status);
}
