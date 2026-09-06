package vn.giapha.events.infrastructure.jdbc;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mở rộng một chi/ngành thành <b>toàn bộ cây con</b> của nó, bằng {@code ltree}.
 *
 * <p>Toán tử {@code <@} của {@code ltree} nghĩa là "là hậu duệ hoặc chính nó". Đây là cách duy nhất
 * đúng để hiểu câu "sự kiện của chi này": giỗ của Chi Nhất phải tới cả Ngành Trưởng và Nhánh Út bên
 * dưới nó, chứ không chỉ những người gắn thẳng vào node Chi Nhất. So sánh {@code target_branch_id =
 * :branchId} thay cho phép so cây là lỗi âm thầm — nó luôn trả ít kết quả hơn, không bao giờ báo
 * lỗi.</p>
 *
 * <p>Chỉ mục {@code ix_branch_path_gist} (GiST trên {@code path}) đỡ đúng phép so này.</p>
 *
 * <h2>Nợ kiến trúc đã biết</h2>
 * {@code branch} thuộc sở hữu của {@code genealogy}, chưa có mặt tiền công khai. Đọc thẳng bằng SQL
 * — không có phụ thuộc Java nào sang context khác nên ranh giới module vẫn sạch; khi
 * {@code genealogy} mở application service thì lớp này chuyển sang gọi service.
 */
@Repository
public class BranchScopeJdbcAdapter {

    private static final String SQL_SUBTREE = """
            SELECT child.id
              FROM branch AS root
              JOIN branch AS child ON child.path <@ root.path
             WHERE root.id = :branchId
               AND child.is_deleted = FALSE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BranchScopeJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Id của chi được chỉ định và mọi nhánh con còn hiệu lực.
     *
     * @return tập rỗng nếu chi không tồn tại — bên gọi phải hiểu là "không khớp gì", tuyệt đối
     *         không được diễn giải ngược thành "không giới hạn"
     */
    @Transactional(readOnly = true)
    public Set<UUID> subtreeOf(UUID branchId) {
        if (branchId == null) {
            return Set.of();
        }
        List<UUID> ids = jdbc.queryForList(SQL_SUBTREE,
                new MapSqlParameterSource("branchId", branchId), UUID.class);
        return Set.copyOf(ids);
    }
}
