package vn.giapha.dataimport.infrastructure.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.giapha.dataimport.domain.port.AnchorAncestorPort;

/**
 * Hiện thực {@link AnchorAncestorPort}: đi ngược lên phía trên một mỏ neo.
 *
 * <h2>Vì sao đọc bảng chiếu chứ không gõ Cypher ở đây</h2>
 * Bất biến của lược đồ nói rõ: cạnh AGE là nguồn chân lý, còn bảng {@code relationship} là
 * <b>bản chiếu ghi trong cùng transaction</b> để phục vụ khoá ngoại, nhật ký và SQL thuần. Hai bên
 * không bao giờ lệch nhau. Vì vậy với một phép <b>chỉ đọc</b> như đi ngược vài chục đời, đọc bản
 * chiếu là hợp lệ và đổi lại được hai thứ đáng giá:
 * <ul>
 *   <li>không phải chép một câu Cypher thứ hai vào context này — hai bản Cypher cho cùng một phép
 *       duyệt là hai bản sẽ lệch nhau khi mô hình cạnh đổi;</li>
 *   <li>không dính bẫy phiên làm việc của AGE (LOAD age cộng search_path), thứ đã có tên riêng
 *       trong tài liệu dự án.</li>
 * </ul>
 * Khi {@code genealogy} mở mặt tiền công khai thì lớp này chuyển sang gọi service duyệt cây của
 * context ấy và bỏ hẳn SQL.
 *
 * <h2>Lặp theo tầng, không đệ quy và không CTE đệ quy</h2>
 * Mỗi vòng lặp lấy <b>toàn bộ cha mẹ của cả tầng</b> bằng một truy vấn, nên số truy vấn bằng số
 * đời chứ không bằng số người. Tối đa {@code maxDepth} vòng, và vòng lặp dừng ngay khi không còn
 * ai mới — nếu đồ thị <b>đã</b> có sẵn một vòng lặp từ dữ liệu cũ thì phép duyệt này vẫn kết thúc,
 * thay vì treo cả luồng web.
 */
@Repository
public class AnchorAncestorJdbcAdapter implements AnchorAncestorPort {

    private static final String SQL_CHA_ME = """
            SELECT DISTINCT r.to_person_id AS child_id, r.from_person_id AS parent_id
              FROM relationship r
             WHERE r.rel_type IN ('PARENT_BIO', 'PARENT_ADOPT')
               AND r.is_deleted = FALSE
               AND r.to_person_id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnchorAncestorJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Canh> ancestorEdges(UUID personId, int maxDepth) {
        if (personId == null || maxDepth <= 0) {
            return List.of();
        }
        Set<UUID> daThay = new LinkedHashSet<>();
        daThay.add(personId);
        List<Canh> canh = new ArrayList<>();
        List<UUID> tang = List.of(personId);

        for (int doi = 0; doi < maxDepth && !tang.isEmpty(); doi++) {
            List<Canh> cuaTang = new ArrayList<>();
            jdbc.query(SQL_CHA_ME, new MapSqlParameterSource("ids", tang),
                    (RowCallbackHandler) rs -> cuaTang.add(
                            new Canh(rs.getObject("child_id", UUID.class),
                                    rs.getObject("parent_id", UUID.class))));
            List<UUID> moi = new ArrayList<>();
            for (Canh c : cuaTang) {
                canh.add(c);
                if (c.cha() != null && daThay.add(c.cha())) {
                    moi.add(c.cha());
                }
            }
            tang = moi;
        }
        return List.copyOf(canh);
    }
}
