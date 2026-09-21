package vn.giapha.content.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập bảng {@code post}.
 *
 * <h2>Luật "ai thấy gì" nằm TRONG SQL, không nằm trong Java</h2>
 * Mệnh đề {@link #VISIBLE_TO_CALLER} là bản duy nhất của luật ấy, và nó được cả
 * {@link #findVisible} lẫn {@link #countVisible} dùng chung — <b>bắt buộc phải chung</b>: một câu
 * đếm rộng hơn câu lấy sẽ cho ra trang cuối rỗng mà giao diện vẫn vẽ nút "Trang sau", còn hẹp hơn
 * thì giấu mất bài.
 *
 * <p>Lọc ở CSDL chứ không ở tiến trình vì lý do thứ hai mới là lý do thật: nạp hết rồi lọc thì
 * <b>chỉ cần một chỗ quên lọc</b> là bản nháp của người khác đi ra ngoài. Cùng khuôn
 * {@code ChangeRequestJpaRepository.PENDING_IN_SCOPE}.
 *
 * <h2>Ba quy ước về tham số, cả ba đều để tránh một cái bẫy đã có thật</h2>
 * <ul>
 *   <li><b>{@code :paths} là một chuỗi</b> — literal mảng của Postgres
 *       ({@code {goc.chi_giap,goc.chi_at}}) rồi ép sang {@code ltree[]}. Truyền mảng Java qua
 *       native query cho hành vi khác nhau giữa các phiên bản driver; một chuỗi thì không. An toàn
 *       tiêm lệnh được bảo đảm ở tầng trên: mọi nhãn đã qua {@code BranchPath}, vốn chỉ nhận
 *       {@code [A-Za-z0-9_.]}.</li>
 *   <li><b>{@code :clanWide} là {@code int}</b> — ánh xạ boolean trong native query phụ thuộc
 *       phương ngữ; {@code 1}/{@code 0} thì không phụ thuộc gì cả.</li>
 *   <li><b>Tham số lọc có thể rỗng đi dưới dạng chuỗi rồi {@code CAST}</b> — Postgres không suy
 *       được kiểu của một tham số đứng cạnh {@code IS NULL}, và thông báo lỗi
 *       ("could not determine data type of parameter") không chỉ về chỗ sai.</li>
 * </ul>
 */
public interface PostJpaRepository extends JpaRepository<PostJpaEntity, UUID> {

    /**
     * Chi của bài, nối <b>trái</b>: bài của tác giả chưa gắn chi vẫn phải ra khỏi câu truy vấn để
     * vai toàn dòng họ còn thấy mà xử lý. Chi đã xoá mềm coi như không tồn tại — {@code b.path}
     * thành {@code NULL} và phép so phạm vi rơi về nhánh "không phân giải được chi đích", tức chỉ
     * vai toàn dòng họ đụng được. Dữ liệu thiếu làm quyền hẹp lại, không nới ra.
     */
    String FROM_POST = """
              FROM post p
              LEFT JOIN branch b ON b.id = p.branch_id AND b.is_deleted = FALSE
            """;

    /**
     * <b>Luật ai thấy gì</b> (design/07-checklist §2, hợp đồng REST của đợt này), một bản duy nhất:
     * <ul>
     *   <li>{@code PUBLISHED} — mọi thành viên;</li>
     *   <li>bài của chính mình — luôn thấy, ở mọi trạng thái;</li>
     *   <li>{@code PENDING}/{@code WITHDRAWN} — người duyệt trong phạm vi {@code ltree};</li>
     *   <li>{@code DRAFT} của người khác — <b>không ai</b>, kể cả Hội đồng. Một bản nháp chưa gửi
     *       là thứ chưa ai được mở ra đọc.</li>
     * </ul>
     */
    String VISIBLE_TO_CALLER = """
             AND (p.status = 'PUBLISHED'
                  OR p.author_user_id = :callerUserId
                  OR (p.status <> 'DRAFT'
                      AND (:clanWide = 1
                           OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))))
            """;

    String STATUS_FILTER = """
             WHERE (CAST(:status AS varchar) IS NULL OR p.status = CAST(:status AS varchar))
            """;

    /**
     * Màn <b>"Bài của tôi"</b>, lọc <b>trong SQL</b>.
     *
     * <p>Không có mệnh đề này thì giao diện buộc phải bắn <i>bốn</i> lượt gọi song song (một cho
     * mỗi trạng thái) rồi lọc tiếp ở client với giới hạn 50 mỗi loại — và một người viết nhiều sẽ
     * <b>mất bài cũ mà không có gì báo</b>: phân trang của máy chủ đếm trên toàn bộ tập, còn phép
     * lọc của client thì chỉ thấy trang đầu.</p>
     *
     * <p>Lọc theo {@code author_user_id} (tài khoản đã bấm nút), không theo
     * {@code author_person_id}: câu hỏi của màn này là <i>"tôi đã viết gì"</i>, và "tôi" ở đây là
     * người đang đăng nhập. Hai cột ấy tách nhau có lý do — xem ghi chú của {@code V17}.</p>
     *
     * <p>Chạy trên index {@code ix_post_author}. Kết hợp với {@code VISIBLE_TO_CALLER} thì thừa
     * một nửa (bài của chính mình luôn thấy được), nhưng giữ cả hai để <b>một</b> mệnh đề quyền
     * duy nhất áp cho mọi lối đọc — bỏ nó đi ở đây là mở một lối thứ hai không ai kiểm.</p>
     */
    String MINE_FILTER = """
             AND (:mine = 0 OR p.author_user_id = :callerUserId)
            """;

    @Query(value = "SELECT p.* " + FROM_POST + STATUS_FILTER + VISIBLE_TO_CALLER + MINE_FILTER
            + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<PostJpaEntity> findVisible(@Param("status") String status,
                                    @Param("callerUserId") UUID callerUserId,
                                    @Param("paths") String paths,
                                    @Param("clanWide") int clanWide,
                                    @Param("mine") int mine,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + FROM_POST + STATUS_FILTER + VISIBLE_TO_CALLER + MINE_FILTER,
            nativeQuery = true)
    long countVisible(@Param("status") String status,
                      @Param("callerUserId") UUID callerUserId,
                      @Param("paths") String paths,
                      @Param("clanWide") int clanWide,
                      @Param("mine") int mine);

    /**
     * Trang chủ. Không có tham số phạm vi nào: bài đã đăng là của <b>cả dòng họ</b>, không phải của
     * riêng chi tác giả — đó chính là lý do nó phải qua tay người duyệt trước.
     */
    @Query(value = """
            SELECT p.* FROM post p
             WHERE p.status = 'PUBLISHED'
             ORDER BY p.published_at DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<PostJpaEntity> findFeed(@Param("limit") int limit);

    @Query(value = "SELECT count(*) " + FROM_POST + """
             WHERE p.status = 'PENDING'
               AND (:clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
            """, nativeQuery = true)
    long countPendingInScope(@Param("paths") String paths, @Param("clanWide") int clanWide);
}
