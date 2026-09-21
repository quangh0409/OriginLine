package vn.giapha.content.infrastructure.branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.content.domain.port.BranchLocatorPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link BranchLocatorPort} bằng SQL <b>chỉ đọc</b> trên {@code branch} và
 * {@code person}.
 *
 * <h2>Vì sao đọc thẳng SQL thay vì gọi sang {@code genealogy}</h2>
 * {@code genealogy.application} không mang {@code @NamedInterface} — cố ý, vì mở cả gói ấy nghĩa
 * là mọi module với tới được {@code Person}, {@code PersonRepository}, {@code PrivacyTierService},
 * tức xoá gần hết ranh giới mà {@code ModularityTests} sinh ra để giữ. Ba context đã đi đúng lối
 * này trước: {@code membership.infrastructure.branch.BranchLookupJdbcAdapter},
 * {@code events.infrastructure.jdbc.BranchScopeJdbcAdapter} và
 * {@code dataimport.api.support.ImportBranchDirectory}. Ở cấp Java không có phụ thuộc nào sang
 * {@code genealogy}, nên ranh giới module vẫn sạch.
 *
 * <h2>TUYỆT ĐỐI chỉ SELECT, và tuyệt đối không đọc dữ liệu cá nhân</h2>
 * Bảng {@code person} xuất hiện ở đây đúng <b>một cột</b>: {@code primary_branch_id}. Không tên,
 * không ngày sinh, không liên hệ. Dữ liệu nhân khẩu chỉ ra khỏi hệ thống qua bộ lọc phân tầng riêng
 * tư của {@code genealogy} — ở context này là {@code AuthorDirectory}. Thêm một cột "tiện thể lấy
 * luôn cái tên" vào câu truy vấn dưới đây là mở một đường vòng qua bộ lọc ấy, và nó sẽ không bị ai
 * phát hiện vì câu SQL vẫn trông hoàn toàn vô hại.
 *
 * <h2>Chi đã xoá mềm coi như không tồn tại</h2>
 * Trả rỗng thay vì trả path. Hệ quả đi đúng hướng an toàn: phép kiểm phạm vi rơi về nhánh "không
 * phân giải được chi đích", tức chỉ vai toàn dòng họ mới đụng được.
 */
@Component
public class BranchLocatorJdbcAdapter implements BranchLocatorPort {

    private static final Logger log = LoggerFactory.getLogger(BranchLocatorJdbcAdapter.class);

    private static final String SQL_BRANCH_PATH = """
            SELECT b.path::text AS branch_path
              FROM branch b
             WHERE b.id = :branchId
               AND b.is_deleted = FALSE
            """;

    private static final String SQL_BRANCH_NAME = """
            SELECT b.name
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

    private final NamedParameterJdbcTemplate jdbc;

    public BranchLocatorJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPath> pathOfBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_BRANCH_PATH,
                new MapSqlParameterSource("branchId", branchId), String.class)).map(BranchPath::of);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nameOfBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        return first(jdbc.queryForList(SQL_BRANCH_NAME,
                new MapSqlParameterSource("branchId", branchId), String.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPath> branchOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        Optional<BranchPath> path = first(jdbc.queryForList(SQL_PERSON_BRANCH_PATH,
                new MapSqlParameterSource("personId", personId), String.class)).map(BranchPath::of);
        if (path.isEmpty()) {
            log.debug("Nhan khau {} chua gan chi nao (hoac chi da xoa mem)", personId);
        }
        return path;
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

    /** Phần tử đầu tiên khác {@code null}; danh sách rỗng ⇒ {@link Optional#empty()}. */
    private static <T> Optional<T> first(List<T> rows) {
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
