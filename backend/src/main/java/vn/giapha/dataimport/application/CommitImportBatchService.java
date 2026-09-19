package vn.giapha.dataimport.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.CommitOrder;
import vn.giapha.dataimport.domain.CommitPreflight;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportCommitBlockedException;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;
import vn.giapha.dataimport.domain.port.ExternalRefPort;
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
 *   <li><b>Soát trước khi ghi</b> — ngoài transaction ghi. Nếu lô chạm phải một câu hỏi Hội đồng
 *       chưa trả lời, hoặc còn vòng lặp, thì danh sách vấn đề phải <b>sống sót</b> để người nhập
 *       đọc. Ghi nó bên trong transaction ghi thì nó cuộn lại cùng lô và màn đối soát trống trơn,
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
    private final ExternalRefPort externalRefs;
    private final DuplicatePairRepository duplicatePairs;
    private final ImportCommitWriter writer;

    public CommitImportBatchService(ImportBatchRepository batches, ImportRowRepository rows,
                                    ImportIssueRepository issues, ExternalRefPort externalRefs,
                                    DuplicatePairRepository duplicatePairs,
                                    ImportCommitWriter writer) {
        this.batches = batches;
        this.rows = rows;
        this.issues = issues;
        this.externalRefs = externalRefs;
        this.duplicatePairs = duplicatePairs;
        this.writer = writer;
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
            throw new IllegalStateException("Lo " + batchId + " dang o trang thai " + batch.status()
                    + " voi " + batch.blockingCount() + " loi chan; chua duoc phep ghi vao pha");
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
        List<ImportIssue> chan = new ArrayList<>(gopKhongApDungDuoc(keHoachGop));
        chan.addAll(CommitPreflight.soat(personRows, marriageRows));
        CommitOrder.KetQua thuTu = CommitOrder.sap(personRows, marriageRows);
        if (thuTu.coVongLap()) {
            chan.add(vongLap(thuTu.dongKetVong()));
        }
        chan.addAll(maThamChieuKhongPhanGiaiDuoc(batch, personRows, marriageRows));
        if (!chan.isEmpty()) {
            chan.sort(ImportIssue.TAT_DINH);
            issues.replaceAll(batchId, chan);
            batches.updateStatus(batchId, BatchStatus.FAILED,
                    chan.size() + " vấn đề phải được Hội đồng Tộc biểu chốt trước khi ghi");
            log.warn("Lo {} dung truoc khi ghi voi {} van de chua chot; khong mot dong nao vao pha",
                    batchId, chan.size());
            throw new ImportCommitBlockedException(
                    "Lô này chạm " + chan.size() + " vấn đề mà quy ước của dòng họ chưa trả lời."
                            + " Chưa một người nào được ghi vào phả.", chan);
        }

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
                    "Xac nhan da xem canh bao phai co chu: thieu app_user cua nguoi bam");
        }
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        if (batch.status().daChot()) {
            throw new IllegalStateException("Lo " + batchId + " da o trang thai " + batch.status()
                    + "; khong con gi de xac nhan");
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

    /**
     * Nhóm gộp không áp dụng được → lỗi chặn, kèm nguyên văn câu giải thích của kế hoạch.
     *
     * <p>Dừng có giải thích còn hơn ghi một nửa: một phép gộp áp dụng dở nghĩa là vài dòng đã bị
     * trỏ lại còn vài dòng thì chưa, và cái nửa vời ấy nằm trong phả chứ không nằm trong log.</p>
     */
    private static List<ImportIssue> gopKhongApDungDuoc(DuplicateMergePlan keHoach) {
        if (!keHoach.coVuongMac()) {
            return List.of();
        }
        List<ImportIssue> found = new ArrayList<>(keHoach.vuongMac().size());
        for (String lyDo : keHoach.vuongMac()) {
            found.add(ImportIssue.caLo(IssueCode.IMP_MERGE_NOT_APPLICABLE, lyDo, Map.of()));
        }
        return found;
    }

    private static ImportIssue vongLap(List<String> ketVong) {
        return ImportIssue.caLo(IssueCode.IMP_COMMIT_CYCLE,
                "Vẫn còn vòng lặp tổ tiên ở bước ghi, trên các mã: " + String.join(", ", ketVong)
                        + ". Bộ kiểm lẽ ra đã chặn từ trước, nên tệp đã đổi giữa hai bước hoặc một"
                        + " luật đang bị vô hiệu. Dừng ở đây là cố ý: sắp thứ tự ghi trên một đồ"
                        + " thị có vòng lặp thì không bao giờ xếp xong.",
                Map.of("ma", ketVong));
    }

    /**
     * Mã tham chiếu không phân giải được tại <b>thời điểm ghi</b>.
     *
     * <p>Bộ kiểm đã soát mã cha/mẹ, nhưng mã ở trang Hôn phối và mã kế tự thì chưa, và giữa lần
     * kiểm với lần bấm ghi có thể đã trôi qua vài ngày. Bỏ qua lặng lẽ một dòng hôn phối không
     * phân giải được nghĩa là một cặp vợ chồng biến mất khỏi phả mà không ai biết — và đó chính là
     * loại mất mát không bao giờ tự lộ ra.</p>
     */
    private List<ImportIssue> maThamChieuKhongPhanGiaiDuoc(ImportBatch batch,
                                                           List<PersonRow> personRows,
                                                           List<MarriageRow> marriageRows) {
        Set<String> trongLo = new LinkedHashSet<>();
        for (PersonRow row : personRows) {
            if (row.externalCode() != null && !row.externalCode().isBlank()) {
                trongLo.add(row.externalCode());
            }
        }
        Set<String> canTra = new LinkedHashSet<>();
        for (MarriageRow m : marriageRows) {
            themNeuLa(canTra, trongLo, m.husbandCode());
            themNeuLa(canTra, trongLo, m.wifeCode());
        }
        for (PersonRow row : personRows) {
            themNeuLa(canTra, trongLo, row.heirOfCode());
        }
        if (canTra.isEmpty()) {
            return List.of();
        }
        Set<String> daBiet = externalRefs.resolve(batch.branchId(), canTra).keySet();

        List<ImportIssue> found = new ArrayList<>();
        for (MarriageRow m : marriageRows) {
            if (!m.duCap()) {
                continue;
            }
            for (String ma : List.of(m.husbandCode(), m.wifeCode())) {
                if (!trongLo.contains(ma) && !daBiet.contains(ma)) {
                    found.add(ImportIssue.honPhoi(IssueCode.IMP_COMMIT_REF_NOT_FOUND, m.rowNo(), "Mã",
                            "Trang Hôn phối dòng " + m.rowNo() + " trỏ tới mã " + ma
                                    + " không có trong tệp và cũng chưa có trong phả của chi này.",
                            Map.of("ma", ma)));
                }
            }
        }
        for (PersonRow row : personRows) {
            String ma = row.heirOfCode();
            if (ma == null || ma.isBlank() || trongLo.contains(ma) || daBiet.contains(ma)) {
                continue;
            }
            found.add(ImportIssue.nhanKhau(IssueCode.IMP_COMMIT_REF_NOT_FOUND, row.rowNo(),
                    "Kế tự cho ai (Mã)",
                    "Dòng " + row.rowNo() + " (" + row.nhan() + ") khai kế tự cho mã " + ma
                            + " không tìm thấy. Quan hệ kế tự là thứ dòng họ coi trọng bậc nhất;"
                            + " ghi lô mà bỏ qua nó thì cụ tuyệt tự vẫn tuyệt tự trong phả.",
                    Map.of("ma", ma)));
        }
        return found;
    }

    private static void themNeuLa(Set<String> dich, Set<String> trongLo, String ma) {
        if (ma != null && !ma.isBlank() && !trongLo.contains(ma)) {
            dich.add(ma.trim());
        }
    }
}
