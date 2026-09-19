package vn.giapha.membership.infrastructure.invite;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.membership.domain.ClanOffice;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link InviteeLookupPort} bằng SQL <b>chỉ đọc</b> trên {@code person},
 * {@code person_name} và {@code branch}.
 *
 * <h2>Nợ kiến trúc đã biết — y hệt {@code BranchLookupJdbcAdapter}</h2>
 * Ba bảng đó thuộc context {@code genealogy}, mà {@code genealogy.application} không được đánh dấu
 * {@code @NamedInterface}. Ở cấp Java không có phụ thuộc nào sang {@code genealogy} nên ranh giới
 * module vẫn sạch; khi {@code genealogy} công bố mặt tiền tra cứu thì đây là lớp duy nhất phải sửa.
 *
 * <h2>Thứ tự ưu tiên khi chọn tên hiển thị</h2>
 * Tên hiển thị mặc định ({@code is_primary}) → tên huý → tên bất kỳ. Ba bậc chứ không phải một, vì
 * bộ dữ liệu nhập hàng loạt hay chỉ có tên huý và một màn mời trống tên là một màn mời vô dụng.
 *
 * <h2>Nhân khẩu đã xoá mềm vẫn trả về</h2>
 * Khác {@code BranchLookupJdbcAdapter}, ở đây <b>không</b> lọc {@code is_deleted}: nơi gọi cần phân
 * biệt "không có người này" (404) với "người này đã bị xoá khỏi phả" (422 kèm lời giải thích cho
 * Trưởng chi). Gộp hai ca lại thì người mời nhận một thông điệp không giúp họ làm gì.
 *
 * <h2>Tên dòng họ lấy từ nhãn cấp 1 của {@code ltree}</h2>
 * {@code subltree(path, 0, 1)} là gốc của cây chi — không phải một hằng số cấu hình, nên một cài
 * đặt phục vụ hai dòng họ vẫn nói đúng tên. Rỗng khi nhân khẩu chưa gắn chi nào.
 *
 * <h2>Tuyệt đối chỉ SELECT</h2>
 * Một câu UPDATE lọt vào đây là hai context cùng ghi một bảng mà không ai biết ai.
 */
@Repository
public class InviteeLookupJdbcAdapter implements InviteeLookupPort {

    private static final Logger log = LoggerFactory.getLogger(InviteeLookupJdbcAdapter.class);

    private static final String SQL_INVITEE = """
            SELECT p.is_alive,
                   p.is_deleted,
                   p.generation,
                   b.id        AS branch_id,
                   b.name      AS branch_name,
                   b.path::text AS branch_path,
                   b.region    AS branch_region,
                   (SELECT root.name FROM branch root
                     WHERE root.path = subltree(b.path, 0, 1)
                       AND root.is_deleted = FALSE) AS clan_name,
                   COALESCE(
                       (SELECT pn.full_name FROM person_name pn
                         WHERE pn.person_id = p.id AND pn.is_primary
                         LIMIT 1),
                       (SELECT pn.full_name FROM person_name pn
                         WHERE pn.person_id = p.id AND pn.name_type = 'HUY'
                         ORDER BY pn.created_at
                         LIMIT 1),
                       (SELECT pn.full_name FROM person_name pn
                         WHERE pn.person_id = p.id
                         ORDER BY pn.created_at
                         LIMIT 1)
                   ) AS display_name
              FROM person p
              LEFT JOIN branch b ON b.id = p.primary_branch_id AND b.is_deleted = FALSE
             WHERE p.id = :personId
            """;

    /**
     * Chức danh dòng tộc.
     *
     * <p>Một người có thể đứng đầu nhiều cấp (vừa Tộc trưởng vừa Trưởng chi gốc). Lấy cấp
     * <b>cao nhất</b> — {@code nlevel} nhỏ nhất — vì đó là chức danh người trong họ gọi.</p>
     */
    private static final String SQL_CLAN_OFFICE = """
            SELECT b.name, b.branch_kind
              FROM branch b
             WHERE b.head_person_id = :personId
               AND b.is_deleted = FALSE
             ORDER BY nlevel(b.path)
             LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public InviteeLookupJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invitee> byId(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(SQL_INVITEE, new MapSqlParameterSource("personId", personId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new Invitee(
                personId,
                (String) row.get("display_name"),
                (Integer) row.get("generation"),
                (UUID) row.get("branch_id"),
                (String) row.get("branch_name"),
                toBranchPath((String) row.get("branch_path")),
                (String) row.get("branch_region"),
                (String) row.get("clan_name"),
                Boolean.TRUE.equals(row.get("is_alive")),
                Boolean.TRUE.equals(row.get("is_deleted"))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClanOffice> clanOfficeOf(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(SQL_CLAN_OFFICE, new MapSqlParameterSource("personId", personId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new ClanOffice((String) row.get("name"), (String) row.get("branch_kind")));
    }

    /**
     * Bỏ qua path hỏng thay vì ném lỗi — cùng lập luận với {@code BranchLookupJdbcAdapter}: thiếu
     * một path chỉ làm quyền <b>hẹp</b> hơn, còn ném lỗi thì một chi sai dữ liệu làm hỏng luồng mời
     * của cả những chi khác.
     */
    private static BranchPath toBranchPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BranchPath.of(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Bo qua branch.path khong hop le ltree: {}", raw);
            return null;
        }
    }
}
