package vn.giapha.demo.writer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp hai thứ mà bộ sinh gia phả <b>không</b> tạo ra, nhưng thiếu chúng thì hai tính năng lõi của
 * Giai đoạn 1 không demo được và không kiểm thử được.
 *
 * <h2>1. Sự kiện giỗ ({@code event})</h2>
 * Bộ sinh tạo nhân khẩu kèm {@code death_lunar}, nhưng {@code GenerateRemindersService} (W5) duyệt
 * bảng {@code event} chứ không duyệt bảng {@code person}. Không có dòng {@code event} nào thì toàn
 * bộ đường ống nhắc giỗ — kênh thông báo chính của MVP theo kế hoạch Giai đoạn 1 — chạy đúng quy
 * trình nhưng không có gì để nhắc. Đây là khe hở giữa hai workstream, không phải lỗi của bên nào.
 *
 * <h2>2. Tài khoản ({@code app_user}, {@code branch_assignment})</h2>
 * Không có dòng nào ánh xạ {@code keycloak_sub → app_user → person} thì:
 * <ul>
 *   <li>hộp thư thông báo trả 403 (đúng logic, nhưng không demo được);</li>
 *   <li><b>không kiểm chứng được RBAC theo chi</b> — mà "Trưởng chi chỉ đụng được nhánh được giao"
 *       là một tiêu chí ra Giai đoạn 1.</li>
 * </ul>
 *
 * <p>Ba {@code sub} dưới đây được <b>ghim cứng</b> trong {@code infra/keycloak/realm-giapha.json}.
 * Ghim là bắt buộc: Keycloak dev-mode chạy H2 không gắn volume, không ghim thì mỗi lần dựng lại
 * container là sinh id mới và mọi ánh xạ ở đây trỏ vào hư không.</p>
 *
 * <p>Toàn bộ thao tác <b>idempotent</b>: chạy lại nhiều lần không nhân bản dữ liệu.</p>
 */
@Component
public class DemoAccountAndEventWriter {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountAndEventWriter.class);

    /** Khớp với `id` đã ghim của từng user trong realm-giapha.json. */
    private static final String SUB_ADMIN = "a0000000-0000-4000-8000-000000000001";
    private static final String SUB_BRANCH_HEAD = "a0000000-0000-4000-8000-000000000002";
    private static final String SUB_MEMBER = "a0000000-0000-4000-8000-000000000003";

    private final JdbcTemplate jdbc;

    public DemoAccountAndEventWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed() {
        int events = seedGioEvents();
        int accounts = seedAccounts();
        log.info("Demo bo sung: {} su kien gio, {} tai khoan da anh xa sang nhan khau", events, accounts);
    }

    /**
     * Một dòng {@code GIO} cho mỗi người đã khuất có ngày mất âm lịch.
     *
     * <p>{@code target_branch_id} lấy theo chi của chính người đó — nhờ vậy
     * {@code RecipientDirectory} gửi nhắc đúng cho người cùng chi/nhánh thay vì cả họ, và ràng buộc
     * "nhắc đúng người" mới có dữ liệu để kiểm thử.</p>
     *
     * <p>Không dùng {@code ON CONFLICT} vì bảng {@code event} không có khoá tự nhiên; thay vào đó
     * {@code NOT EXISTS} theo cặp (người, loại sự kiện) — đúng ngữ nghĩa "mỗi người một giỗ".</p>
     */
    private int seedGioEvents() {
        int inserted = jdbc.update("""
                INSERT INTO event (person_id, event_type, title, lunar_date, is_lunar_based,
                                   is_recurring, target_branch_id, is_clan_level)
                SELECT p.id,
                       'GIO',
                       'Gio ' || COALESCE((
                           -- Ten hien thi khong nam o bang person: moi nguoi co nhieu lop ten
                           -- (huy/tu/hieu/thuy/thuong goi/phap danh). Uu tien dong danh dau
                           -- is_primary, khong co thi lay ten huy.
                           SELECT pn.full_name FROM person_name pn
                           WHERE pn.person_id = p.id
                           ORDER BY pn.is_primary DESC,
                                    CASE pn.name_type WHEN 'HUY' THEN 0 ELSE 1 END
                           LIMIT 1), 'nhan khau'),
                       jsonb_build_object(
                           'day',  (p.death_lunar->>'day')::int,
                           'month', (p.death_lunar->>'month')::int,
                           'leap', COALESCE((p.death_lunar->>'leap')::boolean, false)),
                       TRUE, TRUE, p.primary_branch_id, FALSE
                FROM person p
                WHERE NOT p.is_deleted
                  AND NOT p.is_alive
                  AND p.death_lunar IS NOT NULL
                  AND p.death_lunar ? 'day'
                  AND p.death_lunar ? 'month'
                  AND NOT EXISTS (
                        SELECT 1 FROM event e
                        WHERE e.person_id = p.id AND e.event_type = 'GIO' AND NOT e.is_deleted)
                """);

        // Gio To toan ho: su kien cap dong ho, khong gan nhanh (rang buoc ck_event_scope).
        // Lay ngay gio cua thuy to lam ngay te To - dung tap quan, va cho ta mot su kien
        // clan-level de kiem thu nhanh gui "toan ho" khac han nhanh gui theo chi.
        inserted += jdbc.update("""
                INSERT INTO event (person_id, event_type, title, lunar_date, is_lunar_based,
                                   is_recurring, target_branch_id, is_clan_level)
                SELECT p.id, 'GIO_TO', 'Gio To ho ' || COALESCE(b.name, ''),
                       jsonb_build_object(
                           'day',  (p.death_lunar->>'day')::int,
                           'month', (p.death_lunar->>'month')::int,
                           'leap', false),
                       TRUE, TRUE, NULL, TRUE
                FROM person p
                LEFT JOIN branch b ON b.id = p.primary_branch_id
                WHERE p.generation = 1
                  AND NOT p.is_alive
                  AND p.death_lunar IS NOT NULL
                  AND p.death_lunar ? 'day'
                  AND NOT EXISTS (SELECT 1 FROM event e WHERE e.event_type = 'GIO_TO')
                LIMIT 1
                """);
        return inserted;
    }

    /**
     * Ba tài khoản demo, mỗi tài khoản một vai khác nhau để bộ kiểm thử có đủ ma trận phân quyền:
     * quản trị toàn cục · trưởng chi (chỉ một nhánh) · thành viên thường.
     *
     * <p>Người được gán cố ý chọn <b>người còn sống</b>: người đã khuất là dữ liệu công khai nên
     * không kiểm được phân tầng riêng tư trên chính hồ sơ của mình.</p>
     */
    private int seedAccounts() {
        UUID clanRoot = queryUuid("SELECT id FROM branch WHERE branch_kind = 'DONG_HO' ORDER BY path LIMIT 1");
        UUID chiHai = queryUuid(
                "SELECT id FROM branch WHERE branch_kind = 'CHI' ORDER BY path OFFSET 1 LIMIT 1");
        if (clanRoot == null || chiHai == null) {
            log.warn("Chua co du chi de gan quyen demo — bo qua phan tai khoan");
            return 0;
        }

        int n = 0;
        n += upsertAccount(SUB_ADMIN, "admin.giapha", "Quan tri he thong",
                personInBranch(clanRoot), List.of("ADMIN", "COUNCIL"), null);
        n += upsertAccount(SUB_BRANCH_HEAD, "truongchi", "Truong chi",
                personInBranch(chiHai), List.of("BRANCH_HEAD", "MEMBER"), chiHai);
        n += upsertAccount(SUB_MEMBER, "thanhvien", "Thanh vien",
                personInBranch(chiHai), List.of("MEMBER"), chiHai);
        return n;
    }

    /**
     * Một người <b>còn sống</b> thuộc chi đã cho (hoặc bất kỳ nhánh con nào của nó) và chưa bị
     * tài khoản khác chiếm — {@code ux_app_user_person} là unique nên hai tài khoản không thể
     * cùng trỏ vào một nhân khẩu.
     */
    private UUID personInBranch(UUID branchId) {
        return queryUuid("""
                SELECT p.id
                FROM person p
                JOIN branch b  ON b.id = p.primary_branch_id
                JOIN branch rb ON rb.id = ?
                WHERE b.path <@ rb.path
                  AND p.is_alive AND NOT p.is_deleted
                  AND NOT EXISTS (SELECT 1 FROM app_user u WHERE u.person_id = p.id)
                ORDER BY p.generation DESC, p.id
                LIMIT 1
                """, branchId);
    }

    private int upsertAccount(String sub, String username, String displayName, UUID personId,
            List<String> roleCodes, UUID branchId) {
        if (personId == null) {
            log.warn("Khong tim duoc nhan khau con song de gan cho tai khoan {}", username);
            return 0;
        }
        // Tai khoan da ton tai thi giu nguyen anh xa cu - chay lai khong duoc doi nguoi.
        jdbc.update("""
                INSERT INTO app_user (keycloak_sub, person_id, email, display_name, status, locale)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'vi')
                ON CONFLICT (keycloak_sub) DO NOTHING
                """, sub, personId, username + "@example.test", displayName);

        UUID userId = queryUuid("SELECT id FROM app_user WHERE keycloak_sub = ?", sub);
        if (userId == null) {
            return 0;
        }
        for (String code : roleCodes) {
            // BRANCH_HEAD gan vao dung mot chi; ADMIN/COUNCIL gan pham vi toan ho (branch_id NULL)
            // - MemberScope.isClanWide() doi dung mot dong nhu vay, khong suy ra tu role trong token.
            UUID scope = "BRANCH_HEAD".equals(code) || "MEMBER".equals(code) ? branchId : null;
            jdbc.update("""
                    INSERT INTO branch_assignment (app_user_id, role_id, branch_id, valid_from, note)
                    SELECT ?, r.id, ?, current_date, 'seed demo'
                    FROM role r
                    WHERE r.code = ?
                      AND NOT EXISTS (
                            SELECT 1 FROM branch_assignment ba
                            WHERE ba.app_user_id = ? AND ba.role_id = r.id
                              AND ba.branch_id IS NOT DISTINCT FROM ?)
                    """, userId, scope, code, userId, scope);
        }
        return 1;
    }

    private UUID queryUuid(String sql, Object... args) {
        List<UUID> rows = jdbc.query(sql, (rs, i) -> rs.getObject(1, UUID.class), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Số liệu để {@code DemoDataSeeder} ghi log và để test khẳng định. */
    public Map<String, Long> counts() {
        return Map.of(
                "event", jdbc.queryForObject("SELECT count(*) FROM event", Long.class),
                "app_user", jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class),
                "branch_assignment",
                jdbc.queryForObject("SELECT count(*) FROM branch_assignment", Long.class));
    }
}
