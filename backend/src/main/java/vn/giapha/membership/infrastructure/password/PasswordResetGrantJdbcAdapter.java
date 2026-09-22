package vn.giapha.membership.infrastructure.password;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.membership.domain.PasswordResetGrant;
import vn.giapha.membership.domain.port.PasswordResetGrantRepository;

/**
 * Hiện thực {@link PasswordResetGrantRepository} trên bảng {@code password_reset_grant}.
 *
 * <h2>JDBC chứ không phải JPA, và đó là một lựa chọn chứ không phải sự lười</h2>
 * Hai việc của bảng này đều không hợp với JPA:
 * <ol>
 *   <li><b>Đốt một lượt cấp</b> phải là <i>một</i> lệnh ghi có điều kiện
 *       ({@code UPDATE ... WHERE used_at IS NULL}). Qua JPA thì nó thành đọc-kiểm-ghi, và hai lượt
 *       bấm gần nhau sẽ cùng đọc thấy "chưa dùng" rồi cùng đi tiếp. {@code @Version} <i>có</i> chặn
 *       được ca ấy, nhưng bằng một ngoại lệ khoá lạc quan ở tận lớp ngoài — tức là đúng kết quả,
 *       sai thông điệp.</li>
 *   <li><b>Thu hồi rồi ghi lượt mới</b> vấp {@code ux_password_reset_grant_open} nếu lệnh
 *       {@code INSERT} chạy trước lệnh {@code UPDATE}. Hibernate <b>sắp xếp lại</b> thứ tự trong một
 *       lượt flush (insert trước update), nên thứ tự đọc được trong mã nguồn không phải thứ tự chạy
 *       — đúng cái bẫy mà {@code InvitationFlowIT} phải dựng hẳn một ca để canh. Với JDBC thì thứ
 *       tự trong mã nguồn <i>là</i> thứ tự chạy.</li>
 * </ol>
 *
 * <h2>Không có {@link Propagation#REQUIRES_NEW} ở đây, khác với bộ đếm mã mời</h2>
 * Lượt cấp và dòng {@code audit_log} của nó phải <b>cùng commit hoặc cùng rollback</b>: một liên
 * kết sống mà không có vết trong nhật ký là đúng thứ cả thiết kế này sinh ra để không xảy ra. Vì
 * vậy mọi thao tác ghi ở đây tham gia transaction của lớp gọi.
 */
@Repository
public class PasswordResetGrantJdbcAdapter implements PasswordResetGrantRepository {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetGrantJdbcAdapter.class);

    private static final String COLUMNS = """
            id, target_app_user_id, target_person_id, branch_id, issued_by, issued_at,
            expires_at, used_at, revoked_at, revoked_reason, note
            """;

    private static final String SQL_INSERT = """
            INSERT INTO password_reset_grant
                (id, target_app_user_id, target_person_id, branch_id, issued_by, issued_at,
                 expires_at, note)
            VALUES (:id, :targetAppUserId, :targetPersonId, :branchId, :issuedBy, :issuedAt,
                 :expiresAt, :note)
            """;

    private static final String SQL_BY_ID = "SELECT " + COLUMNS
            + " FROM password_reset_grant WHERE id = :id";

    private static final String SQL_REVOKE_OPEN = """
            UPDATE password_reset_grant
               SET revoked_at = :at, revoked_reason = :reason
             WHERE target_app_user_id = :appUserId
               AND used_at IS NULL
               AND revoked_at IS NULL
            """;

    // PHEP CANH "MOT LAN". Ba dieu kien nam trong MENH DE WHERE chu khong trong mot cau if:
    // chua dung, chua thu hoi, con han. Postgres tra ve so dong da doi, va so ay la cau tra loi
    // duy nhat dang tin cho cau hoi "luot goi nay co phai luot dot duoc khong".
    private static final String SQL_CLAIM = """
            UPDATE password_reset_grant
               SET used_at = :at
             WHERE id = :id
               AND used_at IS NULL
               AND revoked_at IS NULL
               AND expires_at > :at
            """;

    private static final String SQL_RELEASE = """
            UPDATE password_reset_grant
               SET used_at = NULL
             WHERE id = :id
               AND used_at IS NOT NULL
            """;

    private static final String SQL_COUNT_BY_ISSUER = """
            SELECT count(*) FROM password_reset_grant
             WHERE issued_by = :issuedBy
               AND issued_at >= :since
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PasswordResetGrantJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public PasswordResetGrant save(PasswordResetGrant grant) {
        jdbc.update(SQL_INSERT, new MapSqlParameterSource()
                .addValue("id", grant.id())
                .addValue("targetAppUserId", grant.targetAppUserId())
                .addValue("targetPersonId", grant.targetPersonId())
                .addValue("branchId", grant.branchId())
                .addValue("issuedBy", grant.issuedBy())
                .addValue("issuedAt", Timestamp.from(grant.issuedAt()))
                .addValue("expiresAt", Timestamp.from(grant.expiresAt()))
                .addValue("note", grant.note()));
        return grant;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetGrant> byId(UUID id) {
        return jdbc.query(SQL_BY_ID, new MapSqlParameterSource("id", id),
                        PasswordResetGrantJdbcAdapter::map)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public int revokeOpenFor(UUID appUserId, Instant at, String reason) {
        int revoked = jdbc.update(SQL_REVOKE_OPEN, new MapSqlParameterSource()
                .addValue("appUserId", appUserId)
                .addValue("at", Timestamp.from(at))
                .addValue("reason", reason));
        if (revoked > 0) {
            log.info("Thu hoi {} lien ket dat lai mat khau con mo cua app_user {}", revoked,
                    appUserId);
        }
        return revoked;
    }

    @Override
    @Transactional
    public boolean claim(UUID id, Instant at) {
        return jdbc.update(SQL_CLAIM, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("at", Timestamp.from(at))) == 1;
    }

    @Override
    @Transactional
    public void release(UUID id) {
        int released = jdbc.update(SQL_RELEASE, new MapSqlParameterSource("id", id));
        if (released > 0) {
            log.info("Tra lien ket dat lai mat khau {} ve trang thai chua dung: realm tu choi mat"
                    + " khau vi chinh sach, khong mot credential nao bi doi", id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int countIssuedBySince(UUID issuerAppUserId, Instant since) {
        Integer count = jdbc.queryForObject(SQL_COUNT_BY_ISSUER, new MapSqlParameterSource()
                .addValue("issuedBy", issuerAppUserId)
                .addValue("since", Timestamp.from(since)), Integer.class);
        return count == null ? 0 : count;
    }

    private static PasswordResetGrant map(ResultSet rs, int rowNum) throws SQLException {
        return new PasswordResetGrant(
                rs.getObject("id", UUID.class),
                rs.getObject("target_app_user_id", UUID.class),
                rs.getObject("target_person_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("issued_by", UUID.class),
                instant(rs, "issued_at"),
                instant(rs, "expires_at"),
                instant(rs, "used_at"),
                instant(rs, "revoked_at"),
                rs.getString("revoked_reason"),
                rs.getString("note"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
