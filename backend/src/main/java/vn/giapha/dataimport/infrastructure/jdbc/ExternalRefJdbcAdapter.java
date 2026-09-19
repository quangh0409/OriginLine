package vn.giapha.dataimport.infrastructure.jdbc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.port.ExternalRefPort;

/**
 * Hiện thực {@link ExternalRefPort} — <b>khoá bất biến</b> của cả đường ống.
 *
 * <h2>Vì sao mọi truy vấn đều kèm branch_id</h2>
 * Cột {@code Mã} chỉ duy nhất <b>trong một chi</b>. Bốn chi nhập song song hoàn toàn có thể cùng
 * đánh số kiểu 01, 02, 03, và bỏ {@code branch_id} khỏi khoá thì chúng đè lên nhau — một lỗi
 * không lộ ra cho tới khi chi thứ hai nộp bài, tức là sau khi chi thứ nhất đã ghi xong và đi ngủ.
 *
 * <h2>Đọc ở nửa đầu, ghi ở nửa sau — cùng một lớp</h2>
 * Nửa đầu đường ống chỉ <b>tra</b> bảng này. Hai phương thức ghi ở cuối lớp chỉ được gọi từ bước
 * ghi vào phả và từ bước gỡ lô, và luôn <b>trong cùng transaction</b> với lệnh tạo {@code person}:
 * tạo một nhân khẩu mà không ghi khoá bất biến của nó thì lần tải lại kế tiếp sẽ tạo lại y nguyên
 * 400 người ấy một lần nữa, và không ai phát hiện cho tới khi mở phả đồ ra thấy mỗi cụ hai lần.
 */
@Repository
public class ExternalRefJdbcAdapter implements ExternalRefPort {

    /** Hệ mã của đường Excel. Đường GEDCOM ở đợt sau dùng {@code GEDCOM_XREF} trên cùng bảng này. */
    private static final String CODE_SYSTEM = "EXCEL_MA";

    private final NamedParameterJdbcTemplate jdbc;

    public ExternalRefJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, UUID> resolve(UUID branchId, Collection<String> externalCodes) {
        if (externalCodes == null || externalCodes.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("system", CODE_SYSTEM)
                .addValue("codes", externalCodes);
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT r.external_code, r.person_id
                  FROM person_external_ref r
                  JOIN person p ON p.id = r.person_id
                 WHERE r.code_system = :system
                   AND r.branch_id = :branchId
                   AND r.external_code IN (:codes)
                """, params, rs -> {
            result.put(rs.getString("external_code"), rs.getObject("person_id", UUID.class));
        });
        return result;
    }

    @Override
    public Set<String> codesOf(UUID branchId) {
        List<String> codes = jdbc.queryForList("""
                SELECT external_code FROM person_external_ref
                 WHERE code_system = :system AND branch_id = :branchId
                 ORDER BY external_code
                """, new MapSqlParameterSource()
                .addValue("system", CODE_SYSTEM)
                .addValue("branchId", branchId), String.class);
        return new LinkedHashSet<>(codes);
    }

    @Override
    public long countByBranch(UUID branchId) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM person_external_ref
                 WHERE code_system = :system AND branch_id = :branchId
                """, new MapSqlParameterSource()
                .addValue("system", CODE_SYSTEM)
                .addValue("branchId", branchId), Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public Map<String, UUID> ownerBranchOf(Collection<String> externalCodes) {
        if (externalCodes == null || externalCodes.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("system", CODE_SYSTEM)
                .addValue("codes", externalCodes);
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT external_code, branch_id
                  FROM person_external_ref
                 WHERE code_system = :system AND external_code IN (:codes)
                """, params, rs -> {
            // Cung mot ma co the ton tai o hai chi (hai chi tu danh so giong nhau). Khi do phep
            // kiem "ma nay thuoc chi khac" van dung: chi can ton tai MOT chu so huu khac chi dang
            // nhap la du de bat nguoi nhap doi so.
            result.putIfAbsent(rs.getString("external_code"), rs.getObject("branch_id", UUID.class));
        });
        return result;
    }

    @Override
    @Transactional
    public void ghi(UUID branchId, String externalCode, UUID personId, UUID batchId) {
        // ON CONFLICT thay vi kiem-roi-chen: hai nguoi cua cung mot chi bam ghi gan nhu cung luc la
        // chuyen co that, va khoa tu van theo chi o tang tren chi giu duoc trong pham vi mot tien
        // trinh. Trung ma thi giu nguyen person_id cu — dung tinh chat "khong sinh nguoi trung".
        jdbc.update("""
                INSERT INTO person_external_ref (code_system, branch_id, external_code, person_id,
                        first_batch_id)
                VALUES (:system, :branchId, :code, :personId, :batchId)
                ON CONFLICT (code_system, branch_id, external_code) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("system", CODE_SYSTEM)
                .addValue("branchId", branchId)
                .addValue("code", externalCode)
                .addValue("personId", personId)
                .addValue("batchId", batchId));
    }

    @Override
    @Transactional
    public int xoaTheoLo(UUID batchId) {
        return jdbc.update("DELETE FROM person_external_ref WHERE first_batch_id = :batchId",
                new MapSqlParameterSource("batchId", batchId));
    }
}
