package vn.giapha.content.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập bảng {@code honour}.
 *
 * <h2>Chi/ngành đến từ {@code person}, không từ một cột của bảng này</h2>
 * Mọi câu ở đây nối {@code honour → person → branch}. Đó là cái giá của quyết định "không chụp lại
 * chi" (xem {@code Honour}), và nó rẻ: {@code person.primary_branch_id} có chỉ mục, còn cái nó mua
 * được là câu "chi nào có bao nhiêu người đỗ đạt" <b>không sai đi</b> sau một lần chuyển chi.
 *
 * <h2>Lọc theo chi tính cả CÂY CON</h2>
 * {@code b.path <@ (path của :branchId)} — toán tử "là hậu duệ hoặc chính nó" của {@code ltree}.
 * Hỏi "Chi Giáp có bao nhiêu người đỗ đạt" mà không đếm các ngành/cành bên dưới là trả lời sai một
 * câu hỏi nghe có vẻ đã trả lời đúng, và người dùng không có cách nào phát hiện.
 *
 * <p>Ba quy ước tham số ({@code :paths} là chuỗi, {@code :clanWide} là {@code int}, tham số lọc
 * rỗng đi dưới dạng chuỗi rồi {@code CAST}) giống hệt {@code PostJpaRepository} — xem javadoc ở
 * đó.</p>
 */
public interface HonourJpaRepository extends JpaRepository<HonourJpaEntity, UUID> {

    String FROM_HONOUR = """
              FROM honour h
              JOIN person pe ON pe.id = h.person_id
              LEFT JOIN branch b ON b.id = pe.primary_branch_id AND b.is_deleted = FALSE
            """;

    /**
     * Bộ lọc người dùng chọn. {@code h.is_deleted = FALSE} nằm ở đây chứ không ở tầng trên: bản ghi
     * đã gỡ không bao giờ được ra khỏi repository, nếu không thì mỗi lối đọc mới lại phải nhớ một
     * lần — và sẽ có lối quên.
     */
    String FILTERS = """
             WHERE h.is_deleted = FALSE
               AND (CAST(:personId AS uuid) IS NULL OR h.person_id = CAST(:personId AS uuid))
               AND (CAST(:kind AS varchar) IS NULL OR h.kind = CAST(:kind AS varchar))
               AND (CAST(:status AS varchar) IS NULL OR h.status = CAST(:status AS varchar))
               AND (CAST(:branchId AS uuid) IS NULL
                    OR (b.path IS NOT NULL
                        AND b.path <@ (SELECT bb.path FROM branch bb
                                        WHERE bb.id = CAST(:branchId AS uuid))))
            """;

    /**
     * <b>Luật ai được XỬ LÝ</b> — không phải luật ai được XEM.
     *
     * <p>Bản ghi {@code PUBLISHED} đi qua đây hết, rồi bị {@code HonourService} lọc lần thứ hai
     * bằng nhóm trường riêng tư thứ sáu. Bản ghi chưa duyệt / đã từ chối thì chỉ người khai và
     * người duyệt trong phạm vi thấy. Đọc javadoc {@code HonourService} trước khi sửa mệnh đề này:
     * áp bộ lọc riêng tư ở đây sẽ làm hàng đợi duyệt <b>luôn rỗng</b>, vì nhóm trường mới mặc định
     * kín.</p>
     */
    String VISIBLE_TO_CALLER = """
               AND (h.status = 'PUBLISHED'
                    OR h.created_by = :callerUserId
                    OR :clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
            """;

    @Query(value = "SELECT h.* " + FROM_HONOUR + FILTERS + VISIBLE_TO_CALLER
            + " ORDER BY h.year DESC NULLS LAST, h.created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<HonourJpaEntity> findVisible(@Param("personId") String personId,
                                      @Param("kind") String kind,
                                      @Param("branchId") String branchId,
                                      @Param("status") String status,
                                      @Param("callerUserId") UUID callerUserId,
                                      @Param("paths") String paths,
                                      @Param("clanWide") int clanWide,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + FROM_HONOUR + FILTERS + VISIBLE_TO_CALLER,
            nativeQuery = true)
    long countVisible(@Param("personId") String personId,
                      @Param("kind") String kind,
                      @Param("branchId") String branchId,
                      @Param("status") String status,
                      @Param("callerUserId") UUID callerUserId,
                      @Param("paths") String paths,
                      @Param("clanWide") int clanWide);

    @Query(value = "SELECT count(*) " + FROM_HONOUR + """
             WHERE h.is_deleted = FALSE
               AND h.status = 'PENDING'
               AND (:clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
            """, nativeQuery = true)
    long countPendingInScope(@Param("paths") String paths, @Param("clanWide") int clanWide);
}
