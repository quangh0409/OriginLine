package vn.giapha.dataimport.infrastructure.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.giapha.dataimport.domain.port.RollbackGuardPort;

/**
 * Hiện thực {@link RollbackGuardPort}: bốn câu truy vấn trả lời "đã có ai khác động vào chưa".
 *
 * <h2>Vì sao là SQL thuần chứ không đi qua các context kia</h2>
 * Mỗi câu ở đây là một phép <b>đếm</b> trên bảng của context khác ({@code app_user},
 * {@code event}, {@code change_request}). Gọi sang từng context để hỏi "có bản ghi nào trỏ tới
 * những người này không" là dựng bốn cổng mới cho bốn câu hỏi chỉ dùng đúng một chỗ. Đây là cùng
 * loại nợ đã ghi ở {@code EventSubjectPort}, và ranh giới module vẫn sạch ở cấp Java: không một
 * lớp nào của context khác bị nhắc tên. Điều tuyệt đối không làm ở đây là <b>ghi</b> — mọi câu
 * dưới đây chỉ đọc.
 */
@Repository
public class RollbackGuardJdbcAdapter implements RollbackGuardPort {

    private final JdbcTemplate jdbc;

    public RollbackGuardJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> hoSoDaBiSua(UUID batchId) {
        return jdbc.queryForList("""
                SELECT 'Hồ sơ của ' || coalesce(n.full_name, 'mã ' || coalesce(e.external_code, '?'))
                       || ' đã bị sửa sau khi ghi (phiên bản '
                       || e.person_version_at_commit || ' → ' || p.version
                       || '); gỡ lô sẽ nuốt mất sửa đổi đó.'
                  FROM import_commit_entry e
                  JOIN person p ON p.id = e.person_id
                  LEFT JOIN person_name n ON n.person_id = p.id AND n.is_primary = TRUE
                 WHERE e.batch_id = ?
                   AND e.entry_kind = 'PERSON'
                   AND e.person_version_at_commit IS NOT NULL
                   AND p.version <> e.person_version_at_commit
                 ORDER BY 1
                """, String.class, batchId);
    }

    @Override
    public List<String> canhMoiTreoVaoLo(UUID batchId, Instant committedAt) {
        // Canh do CHINH lo nay noi deu co mot dong so cai; moi canh con hieu luc khac cham vao
        // nguoi cua lo la do nguoi khac noi sau do. So sanh theo so cai chu khong theo created_at:
        // hai lenh ghi trong cung mot mili-giay la chuyen co that.
        return jdbc.queryForList("""
                WITH nguoi AS (
                    SELECT person_id FROM import_commit_entry
                     WHERE batch_id = ? AND entry_kind = 'PERSON'
                ), canh_cua_lo AS (
                    SELECT edge_from, edge_to, edge_type FROM import_commit_entry
                     WHERE batch_id = ? AND entry_kind = 'EDGE'
                )
                SELECT 'Có quan hệ mới (' || r.rel_type || ') treo vào người của lô: '
                       || r.from_person_id || ' → ' || r.to_person_id
                       || '; gỡ lô sẽ để đầu kia treo lơ lửng.'
                  FROM relationship r
                 WHERE r.is_deleted = FALSE
                   AND (r.from_person_id IN (SELECT person_id FROM nguoi)
                        OR r.to_person_id IN (SELECT person_id FROM nguoi))
                   AND NOT EXISTS (
                        SELECT 1 FROM canh_cua_lo c
                         WHERE c.edge_from = r.from_person_id
                           AND c.edge_to = r.to_person_id
                           AND c.edge_type = r.rel_type)
                   AND r.created_at >= ?
                 ORDER BY 1
                """, String.class, batchId, batchId, Timestamp.from(committedAt));
    }

    @Override
    public List<String> phuThuocNgoaiPhaHe(UUID batchId) {
        return jdbc.queryForList("""
                WITH nguoi AS (
                    SELECT person_id FROM import_commit_entry
                     WHERE batch_id = ? AND entry_kind = 'PERSON'
                )
                SELECT 'Tài khoản "' || u.display_name || '" đã nhận hồ sơ của một người trong lô'
                  FROM app_user u WHERE u.person_id IN (SELECT person_id FROM nguoi)
                UNION ALL
                SELECT 'Sự kiện "' || e.title || '" đã được lập cho một người trong lô'
                  FROM event e
                 WHERE e.is_deleted = FALSE AND e.person_id IN (SELECT person_id FROM nguoi)
                UNION ALL
                SELECT 'Yêu cầu đính chính ' || c.id || ' đang treo trên một người trong lô'
                  FROM change_request c
                 WHERE c.person_id IN (SELECT person_id FROM nguoi)
                UNION ALL
                SELECT 'Chi "' || b.name || '" đã đặt Trưởng chi là một người trong lô'
                  FROM branch b WHERE b.head_person_id IN (SELECT person_id FROM nguoi)
                ORDER BY 1
                """, String.class, batchId);
    }
}
