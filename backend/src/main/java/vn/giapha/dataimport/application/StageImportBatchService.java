package vn.giapha.dataimport.application;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.ParsedWorkbook;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.SourceKind;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportFileStorePort;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.dataimport.domain.port.WorkbookReaderPort;

/**
 * <b>Bước 1 của đường ống:</b> nhận tệp, đọc thành dòng, đưa vào khu vực chờ.
 *
 * <h2>Bất biến của lớp này</h2>
 * Không một lệnh ghi nào chạm {@code person}, {@code relationship} hay đồ thị AGE. Lô kết thúc ở
 * trạng thái {@link BatchStatus#PARSED} và <b>chưa có ai được thêm vào gia phả</b>. Đó không phải
 * hạn chế tạm thời của đợt này mà là thiết kế: người nhập phải nhìn thấy toàn bộ vấn đề của tệp
 * trước khi bất cứ thứ gì đi vào phả.
 *
 * <h2>Vì sao đọc tệp nằm TRONG transaction</h2>
 * Không nằm trong. Việc đọc tệp diễn ra <b>trước</b> khi mở transaction, và chỉ phần ghi khu vực
 * chờ mới mang {@code @Transactional}. Một tệp 5.000 dòng mất vài giây để phân tích; giữ một
 * transaction mở suốt thời gian đó là giữ khoá vô ích và làm nghẽn người khác.
 */
@Service
public class StageImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(StageImportBatchService.class);

    private final WorkbookReaderPort reader;
    private final RowMapper mapper;
    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final ImportIssueRepository issues;
    private final ImportFileStorePort files;

    public StageImportBatchService(WorkbookReaderPort reader, RowMapper mapper,
                                   ImportBatchRepository batches, ImportRowRepository rows,
                                   ImportIssueRepository issues, ImportFileStorePort files) {
        this.reader = reader;
        this.mapper = mapper;
        this.batches = batches;
        this.rows = rows;
        this.issues = issues;
        this.files = files;
    }

    /**
     * @param force bỏ qua phép chặn bấm-hai-lần. Mã băm chỉ chặn việc <b>ghi lại đúng một tệp đã
     *        ghi</b>; sửa một ô rồi tải lại là bước đối soát bình thường và không bị chặn, vì sửa
     *        một ô là đổi mã băm.
     */
    @Transactional
    public ImportBatch stage(UUID branchId, UUID uploadedBy, String filename, byte[] content,
                             boolean force) {
        guardSize(content, filename);
        String sha = sha256(content);

        if (!force && batches.daGhiTepNay(branchId, sha)) {
            throw new ImportRejectedException("IMP_ALREADY_COMMITTED",
                    "Tệp này đã được ghi vào phả cho chi đang chọn. Nếu thật sự muốn nhập lại thì"
                            + " xác nhận, còn nếu vừa sửa tệp thì tải lên bản đã sửa.");
        }

        ParsedWorkbook parsed = reader.read(new ByteArrayInputStream(content), filename);
        List<PersonRow> personRows = mapper.toPersonRows(parsed.personRows());
        List<MarriageRow> marriageRows = mapper.toMarriageRows(parsed.marriageRows());

        String objectKey = files.store(content, filename, sha).orElse(null);
        if (objectKey == null) {
            // Khong chan lo: doi ong van chay duoc ma khong co kho doi tuong. Nhung phai keu, vi
            // mat tep goc la mat kha nang phan biet "ho chep sai so" voi "ta phan tich sai tep".
            log.warn("Lo nhap lieu cho chi {} khong luu duoc tep goc: kho doi tuong chua duoc noi."
                    + " Truy nguyen ve sau se khong co bang chung.", branchId);
        }

        ImportBatch batch = new ImportBatch(UUID.randomUUID(), branchId, uploadedBy,
                SourceKind.EXCEL, BatchStatus.PARSED, filename, objectKey, sha, content.length,
                personRows.size(), marriageRows.size(), 0, 0, 0, 0,
                null, null, null, null, null, null, null, null, null, Instant.now(), 0L);
        ImportBatch saved = batches.save(batch);

        rows.replacePersonRows(saved.id(), personRows);
        rows.replaceMarriageRows(saved.id(), marriageRows);
        // Lo moi thi danh sach loi phai rong, khong phai ke thua tu lan truoc.
        issues.replaceAll(saved.id(), List.of());
        int cu = batches.supersedeOlder(branchId, saved.id());

        log.info("Lo nhap lieu {} cho chi {}: {} dong nhan khau, {} dong hon phoi, {} lo cu"
                        + " chuyen SUPERSEDED", saved.id(), branchId, personRows.size(),
                marriageRows.size(), cu);
        return saved;
    }

    private void guardSize(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            throw new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                    "Tệp rỗng: " + filename);
        }
        if (content.length > ImportLimits.MAX_FILE_BYTES) {
            throw new ImportRejectedException(ImportRejectedException.FILE_TOO_LARGE,
                    "Tệp nặng " + (content.length / 1024 / 1024) + " MB, vượt trần "
                            + (ImportLimits.MAX_FILE_BYTES / 1024 / 1024) + " MB. Một chi 400"
                            + " người chỉ xuất ra vài trăm KB, nên tệp lớn thế này thường là có"
                            + " ảnh nhúng — xoá ảnh đi rồi tải lại.");
        }
    }

    /** SHA-256 của tệp gốc. Dùng cho đúng một việc: chặn bấm hai lần. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM khong co SHA-256", ex);
        }
    }
}
