package vn.giapha.dataimport.api.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bảng tra chi/ngành cho tầng REST của đường ống nhập liệu: {@code id → (tên, ltree path)}.
 *
 * <h2>Khoản nợ kiến trúc đã biết — và nó là bản sao thứ ba của cùng một khoản nợ</h2>
 * {@code branch} thuộc context {@code genealogy}, mà {@code genealogy.application} <b>không</b> là
 * {@code @NamedInterface} nên không gọi sang được ở cấp Java mà không làm {@code ModularityTests}
 * đỏ. Hai context khác đã đi đúng lối này trước: {@code membership.infrastructure.branch.
 * BranchLookupJdbcAdapter} và {@code events.infrastructure.jdbc.BranchScopeJdbcAdapter}. Ở cấp Java
 * không có phụ thuộc nào sang {@code genealogy}, nên ranh giới module vẫn sạch.
 *
 * <p><b>Chỗ đặt là tạm, không phải chỗ đúng.</b> Lớp này thuộc về {@code infrastructure}; nó nằm
 * trong {@code api.support} vì đợt này chỉ gói {@code api} được mở. Dời đi là một phép di chuyển
 * tệp: không lớp nào ngoài {@code api.support} biết tới nó.
 *
 * <h2>Tuyệt đối chỉ SELECT, và tuyệt đối không chạm bảng {@code person}</h2>
 * Mọi thao tác ghi lên {@code branch} là việc của {@code genealogy}. Và bảng {@code person} không
 * xuất hiện ở đây một dòng nào: dữ liệu nhân khẩu chỉ được ra khỏi hệ thống qua bộ lọc phân tầng
 * riêng tư của {@code genealogy}, không qua một câu SQL của context nhập liệu.
 *
 * <h2>Chi đã xoá mềm coi như không tồn tại</h2>
 * Trả rỗng thay vì trả path. Hệ quả đi đúng hướng an toàn: phép kiểm phạm vi rơi về nhánh "không
 * phân giải được chi đích", tức chỉ vai toàn dòng họ mới đụng được.
 */
@Component
public class ImportBranchDirectory {

    private static final Logger log = LoggerFactory.getLogger(ImportBranchDirectory.class);

    private static final String SQL_BY_ID = """
            SELECT b.id, b.name, b.path::text AS branch_path, b.branch_kind
              FROM branch b
             WHERE b.id = :branchId
               AND b.is_deleted = FALSE
            """;

    private static final String SQL_ALL = """
            SELECT b.id, b.name, b.path::text AS branch_path, b.branch_kind
              FROM branch b
             WHERE b.is_deleted = FALSE
             ORDER BY b.path
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ImportBranchDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Một chi/ngành, đủ đúng ba thứ tầng REST cần.
     *
     * @param path {@code ltree} — <b>căn cứ phạm vi duy nhất</b>, không phải {@code id}
     */
    public record Chi(UUID id, String name, BranchPath path, String kind) {
    }

    @Transactional(readOnly = true)
    public Optional<Chi> byId(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        List<Chi> rows = jdbc.query(SQL_BY_ID, new MapSqlParameterSource("branchId", branchId),
                (rs, i) -> toChi(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("branch_path"), rs.getString("branch_kind")));
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** Mọi chi chưa xoá mềm, đã sắp theo {@code path} để cây hiện ra đúng thứ tự dòng tộc. */
    @Transactional(readOnly = true)
    public List<Chi> all() {
        return jdbc.query(SQL_ALL, (rs, i) -> toChi(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("branch_path"),
                        rs.getString("branch_kind")))
                .stream().filter(java.util.Objects::nonNull).toList();
    }

    /** Nạp một lượt cho cả một danh sách lô — tránh N+1 khi trả nhiều lô của nhiều chi. */
    @Transactional(readOnly = true)
    public Map<UUID, Chi> byIds(java.util.Collection<UUID> branchIds) {
        Map<UUID, Chi> map = new LinkedHashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return map;
        }
        for (Chi chi : all()) {
            if (branchIds.contains(chi.id())) {
                map.put(chi.id(), chi);
            }
        }
        return map;
    }

    /**
     * Bỏ qua path hỏng thay vì ném lỗi.
     *
     * <p>Một {@code branch.path} không hợp lệ {@code ltree} là lỗi dữ liệu ở nơi khác. Để nó làm
     * hỏng phép kiểm phân quyền thì người dùng mất quyền vì một chi <i>khác</i> bị sai dữ liệu.
     * Bỏ qua kèm WARN giữ hướng an toàn: thiếu một path chỉ làm quyền hẹp hơn.</p>
     */
    private static Chi toChi(UUID id, String name, String rawPath, String kind) {
        try {
            return new Chi(id, name, BranchPath.of(rawPath), kind);
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("Bo qua branch.path khong hop le ltree cho chi {}: {}", id, rawPath);
            return null;
        }
    }
}
