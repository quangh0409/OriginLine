package vn.giapha.dataimport.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.CommitOrder;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportCommitBlockedException;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Bước 3 của đường ống:</b> ghi một lô đã chờ duyệt vào phả thật.
 *
 * <h2>Ranh giới ghi của cả hệ thống nằm ở đúng lớp này</h2>
 * Mọi thứ trước đó chỉ chạm các bảng {@code import_*}. Từ đây trở đi mới có {@code person},
 * {@code relationship} và đồ thị AGE. Đó là thứ được kiểm bằng cách <b>đếm ba con số trước và
 * sau</b>, không phải bằng lời hứa.
 *
 * <h2>Ba transaction, và vì sao không gộp làm một</h2>
 * <ol>
 *   <li><b>Soát trước khi ghi</b> — ngoài transaction ghi. Đây là <b>lưới thứ hai</b>: cùng bộ
 *       soát ({@link ImportPreflight}) đã chạy ở cuối bước kiểm, nên bình thường lô sạch tới đây.
 *       Nếu dữ liệu đổi giữa hai bước và lô chạm phải một câu hỏi Hội đồng chưa trả lời, hoặc còn
 *       vòng lặp, thì danh sách vấn đề phải <b>sống sót</b> để người nhập đọc. Ghi nó bên trong transaction ghi thì nó cuộn lại cùng lô và màn đối soát trống trơn,
 *       chỉ còn một câu "thất bại".</li>
 *   <li><b>Ghi</b> — đúng một transaction, tất-cả-hoặc-không.</li>
 *   <li><b>Đánh dấu hỏng</b> — cũng ngoài, và cùng lý do.</li>
 * </ol>
 *
 * <h2>Luồng web không bao giờ chờ ở đây</h2>
 * Phương thức {@link #commit} chạy <b>đồng bộ</b> và có thể mất vài chục giây. Tầng REST phải gọi
 * nó trên một luồng nền rồi trả {@code 202} kèm mã lô — và phải <b>truyền SecurityContext sang
 * luồng ấy</b>, vì mọi lệnh ghi bên dưới đều kiểm quyền theo chi của người gọi; luồng nền không có
 * token sẽ bị chính {@code GenealogyAccessGuard} từ chối, đúng như nó nên làm.
 *
 * <p><b>Cố ý không dùng RabbitMQ cho bước này</b>, dù dự án đã có. Hàng đợi ở đây gắn với ngữ
 * nghĩa thử lại + hàng chết, mà thử lại một lệnh ghi phả là việc sai: lần chạy trước đã cuộn lại
 * sạch, lần sau phải do <b>người</b> bấm sau khi xem lỗi.</p>
 */
@Service
public class CommitImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(CommitImportBatchService.class);

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final ImportIssueRepository issues;
    private final DuplicatePairRepository duplicatePairs;
    private final ImportCommitWriter writer;
    private final ImportPreflight preflight;

    public CommitImportBatchService(ImportBatchRepository batches, ImportRowRepository rows,
                                    ImportIssueRepository issues,
                                    DuplicatePairRepository duplicatePairs,
                                    ImportCommitWriter writer, ImportPreflight preflight) {
        this.batches = batches;
        this.rows = rows;
        this.issues = issues;
        this.duplicatePairs = duplicatePairs;
        this.writer = writer;
        this.preflight = preflight;
    }

    /** Kết quả một lần ghi — thứ tầng REST hiện lên sau khi lô chạy xong. */
    public record KetQua(UUID batchId, int daTao, int daCapNhat, int soCanh, long mili) {
    }

    /**
     * Ghi lô vào phả.
     *
     * @param actorUserId tài khoản bấm ghi; đi vào {@code import_batch.committed_by}
     * @throws ImportCommitBlockedException lô chạm một ca Hội đồng chưa chốt, hoặc còn vòng lặp —
     *         <b>chưa một dòng nào vào phả</b>
     * @throws IllegalStateException lô không ở trạng thái được phép ghi
     */
    public KetQua commit(UUID batchId, UUID actorUserId) {
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        if (!batch.coTheDuyet()) {
            throw new IllegalStateException("Lô " + batchId + " đang ở trạng thái " + batch.status()
                    + " với " + batch.blockingCount() + " lỗi chặn; chưa được phép ghi vào phả");
        }

        List<PersonRow> nguyenVan = rows.personRows(batchId);
        List<MarriageRow> honPhoiNguyenVan = rows.marriageRows(batchId);

        // --- 0. Ap cac quyet dinh "gop" TRUOC moi thu khac ---
        // Phai o day, truoc ca buoc soat: mot dong bi bo vi gop khong duoc di tiep vao bat ky phep
        // kiem nao (no se bi cho la thieu me, la lone node, la vong lap voi chinh dong da gop no),
        // va nhat la khong duoc di vao CommitOrder — sap thu tu ghi tren mot do thi con chua ma
        // chet la cach nhanh nhat de sinh nguoi mo coi.
        DuplicateMergePlan keHoachGop =
                DuplicateMergePlan.cua(duplicatePairs.byBatch(batchId), nguyenVan);
        DuplicateMergePlan.SauGop sauGop = keHoachGop.apDung(nguyenVan, honPhoiNguyenVan);
        List<PersonRow> personRows = sauGop.personRows();
        List<MarriageRow> marriageRows = sauGop.marriageRows();

        // --- 1. Soat truoc khi ghi, NGOAI transaction ghi ---
        // Cung MOT bo soat voi buoc kiem (ImportPreflight). Buoc kiem da chay no roi, nen gap loi o
        // day nghia la du lieu da doi giua hai buoc (pha doi, ma bi tra lai) — luoi thu hai, khong
        // phai noi dau tien nguoi nhap duoc nghe ve van de.
        ImportPreflight.KetQua soat = preflight.soat(batch.branchId(), keHoachGop, sauGop);
        if (!soat.sach()) {
            List<ImportIssue> chan = soat.chan();
            // GHEP vao danh sach da co, KHONG thay ca danh sach: thay bang rieng loi chan thi cac
            // canh bao nguoi nhap da tick "da xem" bien mat, blockingCount thi van la 0 — va man
            // doi soat hien mot lo FAILED ma "khong con loi phai sua", dung cai tinh trang NT-TC-15
            // bat duoc.
            List<ImportIssue> tatCa = preflight.ghepVao(issues.byBatch(batchId), soat);
            issues.replaceAll(batchId, tatCa);
            int loiChan = (int) tatCa.stream().filter(ImportIssue::chan).count();
            batches.updateCounters(batchId, batch.personRowCount(), batch.marriageRowCount(),
                    loiChan, tatCa.size() - loiChan, batch.createCount(), batch.updateCount(),
                    WarningDigest.cua(tatCa));
            batches.updateStatus(batchId, BatchStatus.FAILED,
                    chan.size() + " vấn đề phát sinh ngay trước khi ghi; kiểm lại lô để xem chi tiết");
            log.warn("Lo {} dung truoc khi ghi voi {} van de ma buoc kiem chua thay (du lieu doi"
                    + " giua hai buoc?); khong mot dong nao vao pha", batchId, chan.size());
            throw new ImportCommitBlockedException(
                    "Lô này chạm " + chan.size() + " vấn đề phát sinh sau lần kiểm gần nhất."
                            + " Chưa một người nào được ghi vào phả.", chan);
        }
        CommitOrder.KetQua thuTu = soat.thuTu();

        // --- 2. Ghi: dung MOT transaction ---
        batches.updateStatus(batchId, BatchStatus.COMMITTING, null);
        Instant batDau = Instant.now();
        ImportCommitWriter.KetQua ketQua;
        try {
            ketQua = writer.ghi(batch, thuTu.thuTu(), marriageRows, actorUserId);
        } catch (RuntimeException ex) {
            // --- 3. Hong: transaction da cuon lai, KHONG mot dong nao vao pha ---
            batches.updateStatus(batchId, BatchStatus.FAILED,
                    "Ghi vào phả thất bại và đã cuộn lại toàn bộ: " + ex.getMessage());
            log.error("Ghi lo {} that bai; da cuon lai toan bo, khong ai duoc them vao pha",
                    batchId, ex);
            throw ex;
        }

        if (!keHoachGop.rong()) {
            log.info("Lo {} da ap {} quyet dinh gop: {} dong bi bo va tro lai, {} dong chuyen sang"
                            + " cap nhat ho so da co trong pha", batchId,
                    keHoachGop.maThayThe().size() + keHoachGop.gopVaoHoSoDaCo().size(),
                    keHoachGop.maThayThe().size(), keHoachGop.gopVaoHoSoDaCo().size());
        }
        long mili = Duration.between(batDau, Instant.now()).toMillis();
        // Con so DO THAT trong log — khang dinh thoi gian trong CI la thu hay chop nhay, con so
        // trong log moi dung duoc.
        log.info("Lo {} vao pha trong {} ms: {} nguoi moi, {} nguoi cap nhat, {} canh",
                batchId, mili, ketQua.daTao(), ketQua.daCapNhat(), ketQua.soCanh());
        return new KetQua(batchId, ketQua.daTao(), ketQua.daCapNhat(), ketQua.soCanh(), mili);
    }

    /**
     * Người nhập tick "tôi đã xem cảnh báo".
     *
     * <h2>Vì sao bước này bắt buộc phải có một lối gọi</h2>
     * Một cuốn gia phả thật <b>luôn</b> có cảnh báo: thiếu mẹ, thiếu giới, nghi trùng tên, giỗ
     * không rõ năm — thuỷ tổ thì lần nào cũng sinh {@code IMP_LONE_NODE}. Không có bước này thì
     * <b>mọi</b> lô thật đều mắc kẹt ở {@code VALIDATED} với nút ghi xám, và không ai hiểu vì sao.
     *
     * <h2>Ba thứ được ghi, và vì sao thiếu thứ nào cũng hỏng</h2>
     * <ul>
     *   <li><b>Ai</b> — cái tick này mở khoá nút ghi 400 người vào phả. Nó là một hành vi có trách
     *       nhiệm, không phải một cái tích cho qua; một dấu thời gian vô danh không trả lời được
     *       câu duy nhất người ta sẽ hỏi về sau.</li>
     *   <li><b>Lúc nào</b> — thời điểm do lớp này quyết, không phải {@code now()} trong SQL, để
     *       nó kiểm được trong test thay vì phải tin.</li>
     *   <li><b>Tập cảnh báo nào</b> — vân tay, xem {@link WarningDigest}. Lô được kiểm lại và sinh
     *       cảnh báo mới thì xác nhận cũ <b>tự</b> hết hiệu lực.</li>
     * </ul>
     *
     * <p>Cố ý <b>không</b> từ chối khi lô chẳng có cảnh báo nào: người nhập bấm nút trên một danh
     * sách rỗng là chuyện vô hại, và ném lỗi vào mặt họ vì điều đó chỉ làm màn đối soát khó dùng
     * hơn. Nhưng có ghi lại một dòng log, vì nó thường nghĩa là giao diện đang hiện nút sai lúc.</p>
     *
     * @param actorUserId tài khoản bấm xác nhận; đi vào {@code import_batch.warnings_acknowledged_by}
     * @return lô sau khi ghi nhận — {@code coTheDuyet()} của nó là câu trả lời cuối cùng
     * @throws IllegalStateException lô đã chốt, không còn gì để xác nhận
     */
    public ImportBatch xacNhanDaXemCanhBao(UUID batchId, UUID actorUserId) {
        if (actorUserId == null) {
            // KHONG cho tick vo danh. Day la loi khai mo khoa nut ghi vai tram nguoi vao pha; mot
            // dau thoi gian khong co chu thi nhat ky kiem toan chi noi duoc "luc 21:14 co ai do
            // dong y" — cau vo dung dung vao luc can no nhat.
            throw new IllegalArgumentException(
                    "Xác nhận đã xem cảnh báo phải có chủ: thiếu app_user của người bấm");
        }
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        if (batch.status().daChot()) {
            throw new IllegalStateException("Lô " + batchId + " đã ở trạng thái " + batch.status()
                    + "; không còn gì để xác nhận");
        }
        if (batch.warningCount() == 0) {
            log.info("Lo {} khong co canh bao nao ma van nhan duoc lenh xac nhan tu app_user {};"
                    + " thuong la giao dien dang hien nut sai luc", batchId, actorUserId);
        }
        batches.acknowledgeWarnings(batchId, actorUserId, Instant.now());

        ImportBatch sau = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        log.info("app_user {} xac nhan da xem {} canh bao cua lo {}; nut duyet {}", actorUserId,
                sau.warningCount(), batchId, sau.coTheDuyet() ? "da mo" : "van dong");
        return sau;
    }

    /**
     * Đưa các lô mắc kẹt ở {@code COMMITTING} của một chi về {@code VALIDATED}.
     *
     * <p>Món quà trực tiếp của lối ghi tất-cả-hoặc-không: sau khi tiến trình chết giữa chừng, câu
     * hỏi "vào được một phần chưa?" chỉ có <b>hai</b> đáp án, và một câu đếm trên sổ cái là đủ để
     * biết. Không có sổ cái nào nghĩa là transaction đã cuộn lại sạch — cho người nhập bấm lại.</p>
     *
     * @return số lô đã đưa về VALIDATED
     */
    public int phucHoiSauSuCo(UUID branchId) {
        int n = batches.phucHoiLoKetDangGhi(branchId);
        if (n > 0) {
            log.warn("Da dua {} lo cua chi {} tu COMMITTING ve VALIDATED: lan ghi truoc bi ngat"
                    + " giua chung va da tu cuon lai", n, branchId);
        }
        return n;
    }
}
