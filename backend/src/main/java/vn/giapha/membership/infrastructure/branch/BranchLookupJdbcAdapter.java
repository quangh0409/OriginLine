package vn.giapha.membership.infrastructure.branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.giapha.membership.domain.BranchSummary;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link BranchLookupPort} bằng SQL chỉ-đọc trên {@code branch} và {@code person}.
 *
 * <h2>Nợ kiến trúc đã biết — nguyên văn theo javadoc của cổng</h2>
 * Hai bảng đó thuộc context {@code genealogy}. Phân quyền theo phạm vi bắt buộc phải biết
 * {@code ltree} path của đối tượng, nhưng {@code genealogy.application} không được đánh dấu
 * {@code @NamedInterface} nên không gọi sang được ở cấp Java mà không làm {@code ModularityTests}
 * đỏ. Adapter này vì vậy đọc thẳng bằng SQL; ở cấp Java không có phụ thuộc nào sang
 * {@code genealogy} nên ranh giới module vẫn sạch.
 *
 * <p>Đây là <b>mặt gương</b> của khoản nợ đã ghi trên {@code CallerIdentityPort} bên
 * {@code genealogy}, vốn đang đọc thẳng {@code app_user}/{@code branch_assignment} của
 * {@code membership} vì lý do y hệt. Khi {@code genealogy} công bố mặt tiền tra cứu chi, đây là
 * lớp <b>duy nhất</b> phải sửa.</p>
 *
 * <h2>Chi đã xoá mềm được coi như không tồn tại</h2>
 * Trả rỗng thay vì trả path. Hệ quả đúng hướng an toàn: phân quyền trên một chi đã xoá sẽ rơi về
 * nhánh "không phân giải được chi đích", tức là chỉ vai toàn dòng họ mới đụng được.
 *
 * <h2>Tuyệt đối chỉ SELECT</h2>
 * Mọi thao tác ghi lên {@code branch}/{@code person} là việc của {@code genealogy}. Một câu UPDATE
 * lọt vào đây là hai context cùng ghi một bảng mà không ai biết ai.
 */
@Repository
public class BranchLookupJdbcAdapter implements BranchLookupPort {

    private static final Logger log = LoggerFactory.getLogger(BranchLookupJdbcAdapter.class);

    private static final String SQL_BRANCH_PATH = """
            SELECT b.path::text AS branch_path
              FROM branch b
             WHERE b.id = :branchId
               AND b.is_deleted = FALSE
            """;

    private static final String SQL_PERSON_BRANCH_PATH = """
            SELECT b.path::text AS branch_path
              FROM person p
              JOIN branch b ON b.id = p.primary_branch_id
             WHERE p.id = :personId
               AND b.is_deleted = FALSE
            """;

    private static final String SQL_PERSON_BRANCH_ID = """
            SELECT p.primary_branch_id
              FROM person p
             WHERE p.id = :personId
            """;

    /**
     * Tên gốc của cả dòng họ — chi cấp 1 của cây {@code ltree}.
     *
     * <p>{@code nlevel(path) = 1} là định nghĩa của "gốc"; không dùng {@code parent_id IS NULL} vì
     * hai cách ấy có thể lệch nhau khi dữ liệu được nhập từ ngoài vào, và {@code ltree} mới là thứ
     * mọi phép kiểm phạm vi dựa vào. {@code ORDER BY path} để kết quả tất định nếu có nhiều gốc —
     * ca ấy là lỗi dữ liệu, nhưng một màn hình chập chờn thì khó truy hơn nhiều một màn hình sai
     * ổn định.</p>
     */
    private static final String SQL_BRANCH_SUMMARY = """
            SELECT b.id, b.name, b.branch_kind, b.path::text AS branch_path
              FROM branch b
             WHERE b.id = :branchId
               AND b.is_deleted = FALSE
            """;

    private static final String SQL_CLAN_NAME = """
            SELECT b.name
              FROM branch b
             WHERE nlevel(b.path) = 1
               AND b.is_deleted = FALSE
             ORDER BY b.path
             LIMIT 1
            """;

    private static final String SQL_PERSON_VERSION = """
            SELECT p.version
              FROM person p
             WHERE p.id = :personId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BranchLookupJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPath> pathOfBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_BRANCH_PATH,
                new MapSqlParameterSource("branchId", branchId), String.class))
                .flatMap(BranchLookupJdbcAdapter::toBranchPath);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPath> branchOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_PERSON_BRANCH_PATH,
                new MapSqlParameterSource("personId", personId), String.class))
                .flatMap(BranchLookupJdbcAdapter::toBranchPath);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> branchIdOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_PERSON_BRANCH_ID,
                new MapSqlParameterSource("personId", personId), UUID.class));
    }

    /**
     * Cố ý <b>không</b> lọc {@code is_deleted}: mốc phiên bản của một nhân khẩu đã xoá mềm vẫn là
     * dữ liệu thật, và việc từ chối áp dụng lên bản ghi đã xoá là quyết định của
     * {@code ChangeRequestApplier} bên {@code genealogy} — nơi biết đủ ngữ cảnh để trả đúng mã lỗi.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Long> versionOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_PERSON_VERSION,
                new MapSqlParameterSource("personId", personId), Long.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchSummary> summaryOfBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        SqlParameterSource params = new MapSqlParameterSource("branchId", branchId);
        return first(jdbc.query(SQL_BRANCH_SUMMARY, params, (rs, row) -> new BranchSummary(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("branch_kind"),
                toBranchPath(rs.getString("branch_path")).orElse(null))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> clanName() {
        return first(jdbc.queryForList(SQL_CLAN_NAME, new MapSqlParameterSource(), String.class));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean branchExists(UUID branchId) {
        return pathOfBranch(branchId).isPresent();
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    /**
     * Bỏ qua path hỏng thay vì ném lỗi.
     *
     * <p>Một {@code branch.path} không hợp lệ {@code ltree} là lỗi dữ liệu ở nơi khác. Để nó làm
     * hỏng cả phép kiểm phân quyền thì người dùng mất quyền vì một chi <i>khác</i> bị sai dữ liệu.
     * Bỏ qua kèm WARN giữ hướng an toàn: thiếu một path chỉ làm quyền hẹp hơn.</p>
     */
    private static Optional<BranchPath> toBranchPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BranchPath.of(raw));
        } catch (IllegalArgumentException ex) {
            log.warn("Bo qua branch.path khong hop le ltree: {}", raw);
            return Optional.empty();
        }
    }
}
