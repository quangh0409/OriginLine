package vn.giapha.dataimport.infrastructure.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.CommitEntry;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.port.CommitLedgerPort;

/**
 * Hiện thực {@link CommitLedgerPort} trên bảng {@code import_commit_entry}.
 *
 * <p>Ghi theo lô bằng {@code batchUpdate}: một lô 400 người sinh ra khoảng 1.100 dòng sổ cái, và
 * 1.100 lượt {@code INSERT} riêng lẻ nối thêm vài giây vào một transaction vốn đã dài.</p>
 */
@Repository
public class CommitLedgerJdbcAdapter implements CommitLedgerPort {

    private static final String INSERT = """
            INSERT INTO import_commit_entry (batch_id, entry_kind, row_no, external_code,
                    person_id, person_created, person_version_at_commit,
                    edge_from, edge_to, edge_type, relationship_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public CommitLedgerJdbcAdapter(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    @Transactional
    public void ghi(UUID batchId, List<CommitEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                CommitEntry e = entries.get(i);
                int c = 1;
                ps.setObject(c++, batchId);
                ps.setString(c++, e.kind().name());
                setInt(ps, c++, e.rowNo());
                ps.setString(c++, e.externalCode());
                ps.setObject(c++, e.personId());
                ps.setBoolean(c++, e.personCreated());
                if (e.personVersion() == null) {
                    ps.setNull(c++, Types.BIGINT);
                } else {
                    ps.setLong(c++, e.personVersion());
                }
                ps.setObject(c++, e.edgeFrom());
                ps.setObject(c++, e.edgeTo());
                ps.setString(c++, e.edgeType());
                ps.setObject(c, e.relationshipId());
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }

    @Override
    public List<CommitEntry> doc(UUID batchId) {
        // Nguoi truoc, canh sau — cung la thu tu ma viec go lo can doc nguoc lai.
        return jdbc.query("""
                SELECT entry_kind, row_no, external_code, person_id, person_created,
                       person_version_at_commit, edge_from, edge_to, edge_type, relationship_id
                  FROM import_commit_entry
                 WHERE batch_id = ?
                 ORDER BY entry_kind, row_no NULLS LAST, external_code NULLS LAST, id
                """, MAPPER, batchId);
    }

    @Override
    @Transactional
    public void xoa(UUID batchId) {
        jdbc.update("DELETE FROM import_commit_entry WHERE batch_id = ?", batchId);
    }

    @Override
    public Map<UUID, Long> phienBanCua(List<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> result = new LinkedHashMap<>();
        named.query("SELECT id, version FROM person WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", personIds),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getLong("version"));
                });
        return result;
    }

    @Override
    @Transactional
    public void vaCotNgoai(UUID personId, String maNguyenQuan, LunarDeathDate gioKhongRoNam) {
        if (maNguyenQuan != null && !maNguyenQuan.isBlank()) {
            // Khong dung version: hai cot nay khong thuoc aggregate Person, nen mot lan "sua" o day
            // khong phai mot lan sua ho so ma nguoi khac can duoc canh bao.
            jdbc.update("UPDATE person SET native_place_code = ? WHERE id = ?",
                    maNguyenQuan, personId);
        }
        if (gioKhongRoNam == null) {
            return;
        }
        // CHI va khi cot con trong. Neu genealogy da ghi duoc mot ngay gio day du (co nam) thi ban
        // day du ay thang — no la thu ma nhac gio doc, va no dang nam trong aggregate.
        jdbc.update("""
                UPDATE person
                   SET is_alive = FALSE,
                       death_lunar = CAST(? AS jsonb)
                 WHERE id = ? AND death_lunar IS NULL
                """, gioJson(gioKhongRoNam), personId);
    }

    /**
     * Ngày giỗ thiếu năm, dạng JSONB đúng như {@code ck_person_death_lunar} đòi.
     *
     * <p>Ghi tay thay vì qua Jackson: đúng bốn khoá, không khoá nào là chuỗi do người dùng nhập,
     * nên không có đường nào để một ô Excel chèn ký tự vào JSON. Cố ý <b>không</b> phát sinh khoá
     * {@code year}: có mặt với giá trị {@code null} và vắng mặt là hai chuyện khác nhau khi truy
     * vấn giỗ bằng {@code jsonb_path_ops}.</p>
     */
    private static String gioJson(LunarDeathDate d) {
        StringBuilder sb = new StringBuilder("{\"day\":").append(d.day())
                .append(",\"month\":").append(d.month())
                .append(",\"leap\":").append(d.leap());
        if (d.year() != null) {
            sb.append(",\"year\":").append(d.year());
        }
        return sb.append('}').toString();
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static final RowMapper<CommitEntry> MAPPER = (rs, i) -> {
        // Doc version VA hoi wasNull() ngay lap tuc: wasNull() noi ve cot vua doc gan nhat, nen
        // chen bat ky phep getXxx nao vao giua se bien "chua chup version" thanh "version = 0" —
        // va moi lan go lo se bi tu choi vi "co nguoi da sua" trong khi khong ai sua ca.
        long version = rs.getLong("person_version_at_commit");
        Long phienBan = rs.wasNull() ? null : version;
        return new CommitEntry(
                CommitEntry.Kind.valueOf(rs.getString("entry_kind")),
                (Integer) rs.getObject("row_no"),
                rs.getString("external_code"),
                rs.getObject("person_id", UUID.class),
                rs.getBoolean("person_created"),
                phienBan,
                rs.getObject("edge_from", UUID.class),
                rs.getObject("edge_to", UUID.class),
                rs.getString("edge_type"),
                rs.getObject("relationship_id", UUID.class));
    };
}
