package vn.giapha.dataimport.infrastructure.jdbc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.port.BranchProgressPort;

/**
 * Số liệu nền của màn tiến độ, đọc thẳng bằng SQL.
 *
 * <h2>Vì sao được phép chạm bảng {@code person} ở đây</h2>
 * Vì câu lệnh duy nhất chạm nó là một {@code count(*)}. Không một trường nhân khẩu nào rời khỏi
 * lớp này, nên không có gì để bộ lọc phân tầng riêng tư lọc. Ranh giới thật — "dữ liệu nhân khẩu
 * chỉ ra khỏi hệ thống qua {@code genealogy}" — vẫn nguyên vẹn, và nó sẽ đứt ngay khi ai đó thêm
 * một cột vào câu {@code SELECT} bên dưới. Đừng thêm.
 *
 * <h2>Khoản nợ kiến trúc đã biết, giống hệt hai bản sao trước</h2>
 * {@code branch}, {@code person} và {@code app_user} thuộc các context khác, mà {@code application}
 * của chúng không phải {@code @NamedInterface} nên gọi sang ở cấp Java là làm
 * {@code ModularityTests} đỏ. Ở cấp Java lớp này không phụ thuộc vào context nào khác — chỉ SQL —
 * nên ranh giới module vẫn sạch. Xem {@code ImportBranchDirectory} để biết hai bản sao kia.
 */
@Repository
public class BranchProgressJdbcAdapter implements BranchProgressPort {

    /**
     * Ba nguồn, một câu.
     *
     * <ul>
     *   <li><b>Đã có</b> — {@code count(person)} theo {@code primary_branch_id}, bỏ người đã xoá
     *       mềm. Xoá mềm giữ node lại trong đồ thị nhưng người ấy không còn là một mục cần đối
     *       soát, nên đếm họ vào sẽ báo chi đã xong trong khi chưa.</li>
     *   <li><b>Mẫu số</b> — {@code import_branch_target}. Vắng dòng ⇒ {@code NULL} ⇒ "chưa ai
     *       đếm", khác hẳn 0.</li>
     *   <li><b>Người phụ trách</b> — tài khoản đang giữ vai {@code BRANCH_HEAD} trên đúng chi ấy.
     *       Lấy bản phân công còn hiệu lực theo ngày; hết hạn rồi thì chi coi như chưa có ai
     *       nhận, vì đó đúng là sự thật.</li>
     * </ul>
     *
     * <p>{@code LIMIT 1} trong truy vấn con là cố ý: một chi có thể được giao cho hai người (Trưởng
     * chi và một người phụ giúp) và màn này chỉ có một dòng để hiện. Sắp theo {@code valid_from}
     * mới nhất để con số không nhảy qua lại giữa hai lần tải.</p>
     */
    private static final String SQL = """
            SELECT b.id AS branch_id,
                   (SELECT count(*) FROM person p
                     WHERE p.primary_branch_id = b.id AND p.is_deleted = FALSE) AS persons_in_tree,
                   t.expected_persons,
                   (SELECT u.display_name
                      FROM branch_assignment ba
                      JOIN role r ON r.id = ba.role_id
                      JOIN app_user u ON u.id = ba.app_user_id
                     WHERE ba.branch_id = b.id
                       AND r.code = 'BRANCH_HEAD'
                       AND (ba.valid_from IS NULL OR ba.valid_from <= CURRENT_DATE)
                       AND (ba.valid_to   IS NULL OR ba.valid_to   >= CURRENT_DATE)
                     ORDER BY ba.valid_from DESC NULLS LAST, ba.created_at DESC
                     LIMIT 1) AS coordinator_name
              FROM branch b
              LEFT JOIN import_branch_target t ON t.branch_id = b.id
             WHERE b.id IN (:branchIds)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BranchProgressJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SoLieuChi> theoChi(Collection<UUID> branchIds) {
        Map<UUID, SoLieuChi> ketQua = new LinkedHashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return ketQua;
        }
        List<SoLieuChi> rows = jdbc.query(SQL,
                new MapSqlParameterSource("branchIds", List.copyOf(branchIds)),
                (rs, i) -> new SoLieuChi(
                        rs.getObject("branch_id", UUID.class),
                        rs.getLong("persons_in_tree"),
                        (Integer) rs.getObject("expected_persons"),
                        rs.getString("coordinator_name")));
        for (SoLieuChi row : rows) {
            ketQua.put(row.branchId(), row);
        }
        return ketQua;
    }
}
