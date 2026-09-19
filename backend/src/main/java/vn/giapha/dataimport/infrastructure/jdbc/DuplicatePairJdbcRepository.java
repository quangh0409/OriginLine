package vn.giapha.dataimport.infrastructure.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.DuplicateDecision;
import vn.giapha.dataimport.domain.DuplicatePair;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;

/**
 * Hiện thực {@link DuplicatePairRepository} trên bảng {@code import_duplicate_pair} (V14).
 *
 * <h2>Hợp nhất, không thay thế — và đó là toàn bộ lý do lớp này không giống {@code ImportIssueJdbcRepository}</h2>
 * Kho lỗi xoá sạch rồi ghi lại mỗi lần kiểm. Làm thế ở đây sẽ xoá công đối chiếu của cả buổi
 * chiều mỗi lần ai đó bấm "kiểm lại", nên lối ghi duy nhất là {@code INSERT ... ON CONFLICT
 * (batch_id, pair_key) DO UPDATE} và câu {@code DO UPDATE} <b>cố ý không chạm</b> tới
 * {@code status} / {@code decided_by} / {@code decided_at}.
 *
 * <h2>Xoá cặp không còn dò ra: một câu lệnh, không phải một vòng lặp</h2>
 * Danh sách khoá đi xuống nguyên một lượt qua {@code NOT IN (:keys)}. Lô rỗng cặp thì xoá sạch —
 * và ca ấy phải viết riêng, vì {@code NOT IN ()} với danh sách rỗng là lỗi cú pháp ở PostgreSQL
 * chứ không phải "đúng mọi dòng".
 */
@Repository
public class DuplicatePairJdbcRepository implements DuplicatePairRepository {

    private static final String UPSERT = """
            INSERT INTO import_duplicate_pair (batch_id, pair_key, row_no, incoming_code,
                    counterpart_kind, existing_person_id, existing_code, score, signals)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (batch_id, pair_key) DO UPDATE
               SET row_no       = EXCLUDED.row_no,
                   score        = EXCLUDED.score,
                   signals      = EXCLUDED.signals,
                   last_seen_at = now()
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public DuplicatePairJdbcRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    @Transactional
    public int dongBo(UUID batchId, List<DuplicatePair> pairs) {
        int truoc = demTatCa(batchId);
        if (pairs.isEmpty()) {
            jdbc.update("DELETE FROM import_duplicate_pair WHERE batch_id = ?", batchId);
            return 0;
        }
        Set<String> khoa = new LinkedHashSet<>();
        for (DuplicatePair cap : pairs) {
            khoa.add(cap.pairKey());
        }
        named.update("""
                DELETE FROM import_duplicate_pair
                 WHERE batch_id = :batchId AND pair_key NOT IN (:keys)
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("keys", khoa));

        List<DuplicatePair> danhSach = List.copyOf(pairs);
        jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DuplicatePair p = danhSach.get(i);
                int c = 1;
                ps.setObject(c++, batchId);
                ps.setString(c++, p.pairKey());
                ps.setInt(c++, p.rowNo());
                ps.setString(c++, p.incomingCode());
                ps.setString(c++, p.kind().name());
                ps.setObject(c++, p.existingPersonId());
                ps.setString(c++, p.existingCode());
                ps.setInt(c++, p.score());
                ps.setString(c, p.signals());
            }

            @Override
            public int getBatchSize() {
                return danhSach.size();
            }
        });
        return Math.max(0, demTatCa(batchId) - truoc);
    }

    @Override
    public List<DuplicatePair> byBatch(UUID batchId) {
        return jdbc.query("""
                SELECT * FROM import_duplicate_pair
                 WHERE batch_id = ? ORDER BY row_no, pair_key
                """, MAPPER, batchId);
    }

    @Override
    public Optional<DuplicatePair> byId(UUID pairId) {
        List<DuplicatePair> found = jdbc.query(
                "SELECT * FROM import_duplicate_pair WHERE id = ?", MAPPER, pairId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public int demChuaQuyet(UUID batchId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM import_duplicate_pair
                 WHERE batch_id = ? AND status IN ('PENDING','DEFERRED')
                """, Integer.class, batchId);
        return n == null ? 0 : n;
    }

    @Override
    public int demTatCa(UUID batchId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM import_duplicate_pair WHERE batch_id = ?",
                Integer.class, batchId);
        return n == null ? 0 : n;
    }

    @Override
    @Transactional
    public boolean ghiQuyetDinh(UUID pairId, DuplicateDecision decision, UUID actorUserId,
                                Instant at, String note) {
        // Ba thu di cung mot cau lenh: khong co khe ho nao giua "quyet gi" va "ai quyet".
        // Rang buoc ck_dup_pair_decided o V14 tu choi moi to hop khuyet.
        return jdbc.update("""
                UPDATE import_duplicate_pair
                   SET status = ?, decided_by = ?, decided_at = ?, decision_note = ?
                 WHERE id = ?
                """, decision.name(), actorUserId, Timestamp.from(at), note, pairId) == 1;
    }

    // -------------------------------------------------------------------------------------

    private static final RowMapper<DuplicatePair> MAPPER = (rs, i) -> {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new DuplicatePair(
                rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getString("pair_key"),
                rs.getInt("row_no"),
                rs.getString("incoming_code"),
                DuplicatePair.Kind.valueOf(rs.getString("counterpart_kind")),
                rs.getObject("existing_person_id", UUID.class),
                rs.getString("existing_code"),
                rs.getInt("score"),
                rs.getString("signals"),
                DuplicateDecision.valueOf(rs.getString("status")),
                rs.getObject("decided_by", UUID.class),
                decidedAt == null ? null : decidedAt.toInstant(),
                rs.getString("decision_note"));
    };
}
