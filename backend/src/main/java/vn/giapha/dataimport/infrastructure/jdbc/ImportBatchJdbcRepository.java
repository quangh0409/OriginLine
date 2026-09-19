package vn.giapha.dataimport.infrastructure.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.SourceKind;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;

/**
 * Hiện thực {@link ImportBatchRepository} bằng JDBC thuần.
 *
 * <h2>Vì sao JDBC chứ không phải JPA ở context này</h2>
 * Các bảng {@code import_*} là bảng <b>vào-ra theo lô</b>: ghi 400 dòng một lần, đọc 400 dòng một
 * lần, xoá sạch rồi ghi lại. Không có aggregate nào sống lâu, không có quan hệ nào cần lazy load,
 * và không có ai sửa một dòng chờ đơn lẻ. Đưa JPA vào đây chỉ thêm một tầng entity phải đồng bộ
 * tay với migration, cộng với việc chèn 400 bản ghi qua {@code EntityManager} chậm hơn hẳn một
 * {@code batchUpdate}. Ranh giới hexagonal vẫn nguyên: domain chỉ thấy các port.
 */
@Repository
public class ImportBatchJdbcRepository implements ImportBatchRepository {

    private static final String COLUMNS = """
            id, branch_id, uploaded_by, source_kind, status, original_filename, object_key,
            file_sha256, file_size_bytes, person_row_count, marriage_row_count, blocking_count,
            warning_count, create_count, update_count, warnings_digest, warnings_acknowledged_at,
            warnings_acknowledged_by, warnings_acknowledged_digest, validated_at, committed_at,
            committed_by, rolled_back_at, failure_reason, created_at, version
            """;

    /** Trạng thái "chưa chốt": lô người nhập còn quay lại làm tiếp được. */
    private static final String DANG_MO = "('DRAFT','PARSED','VALIDATING','VALIDATED','FAILED','COMMITTING')";

    /** Trần một trang. Trên ngưỡng này thì đó không còn là màn đối soát mà là một bản kết xuất. */
    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public ImportBatchJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ImportBatch save(ImportBatch batch) {
        jdbc.update("""
                INSERT INTO import_batch (id, branch_id, uploaded_by, source_kind, status,
                        original_filename, object_key, file_sha256, file_size_bytes,
                        person_row_count, marriage_row_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batch.id(), batch.branchId(), batch.uploadedBy(), batch.sourceKind().name(),
                batch.status().name(), batch.originalFilename(), batch.objectKey(),
                batch.fileSha256(), batch.fileSizeBytes(), batch.personRowCount(),
                batch.marriageRowCount(), Timestamp.from(batch.createdAt()));
        return batch;
    }

    @Override
    public Optional<ImportBatch> byId(UUID id) {
        List<ImportBatch> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM import_batch WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, BatchStatus status, String failureReason) {
        jdbc.update("""
                UPDATE import_batch
                   SET status = ?,
                       failure_reason = ?,
                       validated_at = CASE WHEN ? = 'VALIDATED' THEN now() ELSE validated_at END,
                       version = version + 1
                 WHERE id = ?
                """, status.name(), failureReason, status.name(), id);
    }

    @Override
    @Transactional
    public void updateCounters(UUID id, int personRows, int marriageRows, int blocking,
                               int warnings, int creates, int updates, String warningsDigest) {
        jdbc.update("""
                UPDATE import_batch
                   SET person_row_count = ?, marriage_row_count = ?, blocking_count = ?,
                       warning_count = ?, create_count = ?, update_count = ?,
                       warnings_digest = ?, version = version + 1
                 WHERE id = ?
                """, personRows, marriageRows, blocking, warnings, creates, updates, warningsDigest,
                id);
    }

    @Override
    @Transactional
    public int supersedeOlder(UUID branchId, UUID keepBatchId) {
        // CO Y khong dung toi lo da COMMITTED hay dang COMMITTING: mot lo da vao pha thi khong con
        // la "ban nhap cu" nua, va danh dau no SUPERSEDED se lam mat dau vet nguoi nao tu lo nao.
        return jdbc.update("""
                UPDATE import_batch
                   SET status = 'SUPERSEDED', version = version + 1
                 WHERE branch_id = ?
                   AND id <> ?
                   AND status IN ('DRAFT','PARSED','VALIDATING','VALIDATED','FAILED')
                """, branchId, keepBatchId);
    }

    @Override
    public boolean daGhiTepNay(UUID branchId, String fileSha256) {
        // rolled_back_at IS NULL: mot lo da rut lai thi dung cai tep ay PHAI tai len lai duoc, neu
        // khong thi nguoi vua go vi nhap nham chi se khong nhap lai noi cho chi dung.
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM import_batch
                 WHERE branch_id = ? AND file_sha256 = ? AND status = 'COMMITTED'
                   AND rolled_back_at IS NULL
                """, Integer.class, branchId, fileSha256);
        return n != null && n > 0;
    }

    @Override
    @Transactional
    public boolean acknowledgeWarnings(UUID batchId, UUID actorUserId, Instant at) {
        // warnings_acknowledged_digest = warnings_digest ngay TRONG cau UPDATE: khong co khe ho nao
        // giua "tap canh bao nguoi ta vua doc" va "tap canh bao duoc ghi la da doc". Doc digest ra
        // Java roi ghi nguoc vao thi giua hai buoc ay mot lan kiem lai co the da chen vao.
        //
        // KHONG co dieu kien `warnings_acknowledged_at IS NULL`: tick lai SAU khi kiem lai sinh
        // canh bao moi la ca dung nhat cua phuong thuc nay, va chan no di thi lo ket vinh vien.
        return jdbc.update("""
                UPDATE import_batch
                   SET warnings_acknowledged_at = ?,
                       warnings_acknowledged_by = ?,
                       warnings_acknowledged_digest = warnings_digest,
                       version = version + 1
                 WHERE id = ?
                """, Timestamp.from(at), actorUserId, batchId) > 0;
    }

    @Override
    @Transactional
    public void khoaChi(UUID branchId) {
        // Khoa tu van cap transaction: tu nha khi transaction ket thuc, du commit hay rollback.
        // hashtext tren mot chuoi co tien to rieng de khong dung ham y khoa cua nghiep vu khac.
        // query(...) chu khong phai update(...): pg_advisory_xact_lock la mot ham TRA VE KET QUA,
        // va executeUpdate() tren mot cau SELECT bi driver Postgres tu choi thang.
        // CAST(? AS text) vi driver khong suy duoc kieu tham so cho hashtext().
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(CAST(? AS text)))",
                (ResultSetExtractor<Void>) rs -> null, "import:" + branchId);
    }

    @Override
    @Transactional
    public void markCommitted(UUID id, UUID committedBy) {
        jdbc.update("""
                UPDATE import_batch
                   SET status = 'COMMITTED',
                       committed_at = now(),
                       committed_by = ?,
                       failure_reason = NULL,
                       version = version + 1
                 WHERE id = ?
                """, committedBy, id);
    }

    @Override
    @Transactional
    public void markRolledBack(UUID id, UUID rolledBackBy, String reason) {
        jdbc.update("""
                UPDATE import_batch
                   SET rolled_back_at = now(),
                       rolled_back_by = ?,
                       rollback_reason = ?,
                       version = version + 1
                 WHERE id = ? AND rolled_back_at IS NULL
                """, rolledBackBy, reason, id);
    }

    @Override
    @Transactional
    public int phucHoiLoKetDangGhi(UUID branchId) {
        return jdbc.update("""
                UPDATE import_batch b
                   SET status = 'VALIDATED',
                       failure_reason = 'Lần ghi trước bị ngắt giữa chừng nên đã tự cuộn lại;'
                                     || ' không ai được thêm vào phả. Bấm ghi lại.',
                       version = version + 1
                 WHERE b.branch_id = ?
                   AND b.status = 'COMMITTING'
                   AND NOT EXISTS (SELECT 1 FROM import_commit_entry e WHERE e.batch_id = b.id)
                """, branchId);
    }

    // -------------------------------------------------------------------------------------
    // Đường đọc
    // -------------------------------------------------------------------------------------

    @Override
    public List<ImportBatch> byBranch(UUID branchId, BatchStatus status, int page, int size) {
        return branchId == null ? List.of() : byBranches(List.of(branchId), status, page, size);
    }

    @Override
    public List<ImportBatch> byBranches(Collection<UUID> branchIds, BatchStatus status, int page,
                                        int size) {
        if (branchIds == null || branchIds.isEmpty()) {
            // Rong THAT SU la rong: nguoi goi khong quan chi nao. Bo qua bo loc de "tra cho het"
            // la bien mot pham vi trong thanh mot pham vi toan bo — dung chieu sai.
            return List.of();
        }
        List<Object> args = new ArrayList<>(branchIds);
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM import_batch WHERE branch_id IN (").append(chamHoi(branchIds.size()))
                .append(")");
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = Math.max(page, 0) * limit;
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public Optional<ImportBatch> latestOpenBatch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        List<ImportBatch> rows = jdbc.query("SELECT " + COLUMNS + " FROM import_batch"
                + " WHERE branch_id = ? AND status IN " + DANG_MO
                + " ORDER BY created_at DESC, id DESC LIMIT 1", MAPPER, branchId);
        return rows.stream().findFirst();
    }

    @Override
    public Map<UUID, TinhHinhChi> tongHopTheoChi(Collection<UUID> branchIds) {
        Map<UUID, TinhHinhChi> ketQua = new LinkedHashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return ketQua;
        }
        Object[] args = branchIds.toArray();
        String in = chamHoi(args.length);

        // Chi khong co lo nao VAN phai co mat trong ban do: "vang mat" va "chua bat dau" la hai cau
        // tra loi khac nhau, va man tien do can cau thu hai.
        for (UUID id : branchIds) {
            ketQua.put(id, new TinhHinhChi(id, 0, 0, null));
        }

        jdbc.query("""
                SELECT branch_id,
                       count(*) AS so_lo,
                       count(*) FILTER (WHERE status = 'COMMITTED' AND rolled_back_at IS NULL)
                           AS so_lo_da_ghi
                  FROM import_batch
                 WHERE branch_id IN (""" + in + ") GROUP BY branch_id", (RowCallbackHandler) rs -> {
            UUID id = rs.getObject("branch_id", UUID.class);
            TinhHinhChi cu = ketQua.get(id);
            if (cu != null) {
                ketQua.put(id, new TinhHinhChi(id, rs.getInt("so_lo"), rs.getInt("so_lo_da_ghi"),
                        cu.loDangMo()));
            }
        }, args);

        // DISTINCT ON: mot cau cho ca dong ho thay vi mot cau moi chi. Voi muoi chi, N+1 o day la
        // muoi lan khu hoi CSDL cho mot man hinh chi de doc.
        for (ImportBatch lo : jdbc.query("SELECT DISTINCT ON (branch_id) " + COLUMNS
                + " FROM import_batch WHERE branch_id IN (" + in + ") AND status IN " + DANG_MO
                + " ORDER BY branch_id, created_at DESC, id DESC", MAPPER, args)) {
            TinhHinhChi cu = ketQua.get(lo.branchId());
            if (cu != null) {
                ketQua.put(lo.branchId(),
                        new TinhHinhChi(lo.branchId(), cu.soLo(), cu.soLoDaGhi(), lo));
            }
        }
        return ketQua;
    }

    /** {@code ?, ?, ?} — chỗ giữ tham số, không bao giờ là giá trị nối chuỗi. */
    private static String chamHoi(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    private static final RowMapper<ImportBatch> MAPPER = (rs, i) -> new ImportBatch(
            rs.getObject("id", UUID.class),
            rs.getObject("branch_id", UUID.class),
            rs.getObject("uploaded_by", UUID.class),
            SourceKind.valueOf(rs.getString("source_kind")),
            BatchStatus.valueOf(rs.getString("status")),
            rs.getString("original_filename"),
            rs.getString("object_key"),
            rs.getString("file_sha256"),
            rs.getLong("file_size_bytes"),
            rs.getInt("person_row_count"),
            rs.getInt("marriage_row_count"),
            rs.getInt("blocking_count"),
            rs.getInt("warning_count"),
            rs.getInt("create_count"),
            rs.getInt("update_count"),
            trim(rs.getString("warnings_digest")),
            instant(rs.getTimestamp("warnings_acknowledged_at")),
            rs.getObject("warnings_acknowledged_by", UUID.class),
            trim(rs.getString("warnings_acknowledged_digest")),
            instant(rs.getTimestamp("validated_at")),
            instant(rs.getTimestamp("committed_at")),
            rs.getObject("committed_by", UUID.class),
            instant(rs.getTimestamp("rolled_back_at")),
            rs.getString("failure_reason"),
            instant(rs.getTimestamp("created_at")),
            rs.getLong("version"));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * Cắt khoảng trắng đuôi của {@code CHAR(64)}.
     *
     * <p>Postgres đệm {@code CHAR(n)} cho đủ độ dài, nên đọc thẳng ra Java sẽ cho một chuỗi dài
     * đúng 64 ký tự kể cả khi giá trị ngắn hơn — và phép so sánh hai vân tay sẽ sai đúng vào lúc
     * nó quan trọng nhất.</p>
     */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String cut = value.trim();
        return cut.isEmpty() ? null : cut;
    }
}
