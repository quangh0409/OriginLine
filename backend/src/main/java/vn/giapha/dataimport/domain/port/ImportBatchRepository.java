package vn.giapha.dataimport.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.ImportBatch;

/** Kho lô nhập liệu. */
public interface ImportBatchRepository {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> byId(UUID id);

    /** Đổi trạng thái, kèm lý do khi chuyển sang FAILED. */
    void updateStatus(UUID id, BatchStatus status, String failureReason);

    /**
     * Cập nhật các con số tóm tắt sau mỗi lần kiểm.
     *
     * @param warningsDigest vân tay của <b>tập cảnh báo vừa sinh ra</b>. Đi cùng các con số vì nó
     *        thuộc đúng một thời điểm: lệch với {@code warnings_acknowledged_digest} là bằng chứng
     *        lần kiểm này đã sinh cảnh báo mới, và cái tick cũ tự hết hiệu lực mà không ai phải
     *        nhớ đi xoá. {@code null} khi lô không có cảnh báo nào.
     */
    void updateCounters(UUID id, int personRows, int marriageRows,
                        int blocking, int warnings, int creates, int updates,
                        String warningsDigest);

    /**
     * Đánh dấu mọi lô cũ hơn của cùng một chi là SUPERSEDED.
     *
     * <p>Cố ý <b>không xoá</b> chúng: lô cũ là dấu vết của việc đối soát đã đi qua những lần nào,
     * và dung lượng không đáng kể — bốn chi nhân mươi lần tải lại nhân 400 dòng là vài chục nghìn
     * dòng chờ.</p>
     *
     * @return số lô bị đánh dấu
     */
    int supersedeOlder(UUID branchId, UUID keepBatchId);

    /** True nếu chi này đã từng có một lô được ghi vào phả với đúng tệp ấy, và chưa gỡ lại. */
    boolean daGhiTepNay(UUID branchId, String fileSha256);

    /**
     * Ghi nhận người nhập đã xem cảnh báo: <b>ai</b>, <b>lúc nào</b>, và <b>tập cảnh báo nào</b>.
     *
     * <h2>Đây là một hành vi có trách nhiệm, không phải một cái tích cho qua</h2>
     * Lỗi chặn thì không cho bấm duyệt; cảnh báo thì cho — <b>sau khi</b> tick. Không có bước này
     * thì mọi lô thật đều mắc kẹt, vì một cuốn gia phả thật luôn có cảnh báo: thiếu mẹ, thiếu
     * giới, nghi trùng tên. Nhưng bỏ luôn phép tick đi thì cảnh báo trở thành thứ không ai đọc.
     *
     * <p>Ba thứ được ghi cùng lúc, và thiếu thứ nào cũng làm bản ghi vô dụng đúng lúc cần nó:</p>
     * <ul>
     *   <li>{@code actorUserId} — cái tick mở khoá nút ghi 400 người vào phả; một dấu thời gian vô
     *       danh không trả lời được "ai đồng ý".</li>
     *   <li>{@code at} — do tầng application truyền vào chứ không phải {@code now()} trong SQL, để
     *       thời điểm ấy kiểm được trong test thay vì phải tin.</li>
     *   <li><b>Vân tay tập cảnh báo</b> — chép thẳng từ {@code warnings_digest} ngay trong câu
     *       UPDATE, nên không có khe hở nào giữa "cái người ta đọc" và "cái được ghi là đã
     *       đọc".</li>
     * </ul>
     *
     * @return {@code true} nếu có dòng được cập nhật
     */
    boolean acknowledgeWarnings(UUID batchId, UUID actorUserId, Instant at);

    /**
     * Khoá tư vấn theo chi, giữ tới hết transaction hiện tại.
     *
     * <p>Bốn chi nhập song song, và chuyện hai người cùng động vào <b>một</b> chi là có thật —
     * Trưởng chi và một người phụ giúp. Hai lô của cùng một chi không bao giờ được ghi chồng nhau:
     * lô sau sẽ thấy khoá bất biến mà lô trước vừa tạo, tức là ra UPDATE thay vì CREATE, đúng ý đồ
     * chống sinh trùng. Không có khoá này thì cả hai cùng thấy "chưa có ai mang mã đó" và cùng tạo
     * mới.</p>
     */
    void khoaChi(UUID branchId);

    /** Đánh dấu lô đã vào phả. Gọi <b>trong</b> transaction ghi, để trạng thái và dữ liệu cùng commit. */
    void markCommitted(UUID id, UUID committedBy);

    /**
     * Đánh dấu lô đã được gỡ.
     *
     * <p>Trạng thái vẫn là {@code COMMITTED}: lô ấy <b>đã</b> từng vào phả, đó là sự thật lịch sử
     * và {@code committed_at} là mốc so sánh của mọi phép kiểm "ai đã động vào sau khi ghi". "Đã
     * gỡ" là một cột riêng, không phải một trạng thái.</p>
     */
    void markRolledBack(UUID id, UUID rolledBackBy, String reason);

    /**
     * Các lô của một chi đang mắc kẹt ở {@code COMMITTING} mà chưa hề ghi được gì.
     *
     * <p>Phần thưởng trực tiếp của lối ghi tất-cả-hoặc-không: sau khi tiến trình chết giữa chừng,
     * câu hỏi "vào được một phần chưa?" chỉ có hai đáp án, và đúng một câu đếm là đủ để biết. Lô
     * nào không có dòng sổ cái nào thì transaction đã cuộn lại — đưa về {@code VALIDATED} để người
     * nhập bấm lại.</p>
     *
     * @return số lô đã được đưa về VALIDATED
     */
    int phucHoiLoKetDangGhi(UUID branchId);

    // -------------------------------------------------------------------------------------
    // Đường đọc: danh sách lô, và tình hình từng chi
    // -------------------------------------------------------------------------------------

    /**
     * Các lô của <b>một</b> chi, mới nhất trước.
     *
     * <p>Đây là thứ làm cho "đóng trình duyệt rồi quay lại hôm sau" chạy được: trạng thái dở dang
     * sống ở máy chủ, không ở máy người dùng.</p>
     *
     * @param status lọc theo trạng thái; {@code null} là mọi trạng thái
     * @param page  số trang, tính từ 0
     * @param size  số dòng mỗi trang
     */
    List<ImportBatch> byBranch(UUID branchId, BatchStatus status, int page, int size);

    /**
     * Các lô của <b>nhiều</b> chi cùng lúc — phục vụ {@code GET /import/batches} khi người gọi
     * không nêu chi nào.
     *
     * <h2>Vì sao phải có bản nhiều chi thay vì gọi {@link #byBranch} nhiều lần</h2>
     * Phân trang. Gộp bốn danh sách đã phân trang riêng rồi cắt lại cho ra một trang <b>sai</b>:
     * trang 2 của phép gộp không phải là gộp của bốn trang 2. Phạm vi người gọi được quyết ở tầng
     * api rồi truyền xuống đây thành một tập khoá, nên câu lệnh vẫn chỉ có một.
     *
     * @param branchIds tập chi người gọi được phép đọc; rỗng thì trả danh sách rỗng
     */
    List<ImportBatch> byBranches(Collection<UUID> branchIds, BatchStatus status, int page, int size);

    /**
     * Lô đang mở gần nhất của một chi — lô mà người nhập sẽ quay lại làm tiếp.
     *
     * <p>"Đang mở" = chưa chốt: mọi trạng thái trừ {@code COMMITTED} và {@code SUPERSEDED}. Một chi
     * chỉ có tối đa một lô như vậy, vì mỗi lần tải lên đều đánh SUPERSEDED các lô cũ — nhưng câu
     * truy vấn vẫn lấy bản mới nhất thay vì tin vào bất biến ấy.</p>
     */
    Optional<ImportBatch> latestOpenBatch(UUID branchId);

    /**
     * Tình hình nhập liệu của một chi, đã gộp sẵn.
     *
     * @param soLo tổng số lô từng tải lên — dùng để phân biệt "chưa ai bắt đầu" với "đang làm dở"
     * @param soLoDaGhi số lô đã vào phả và <b>chưa</b> bị gỡ
     * @param loDangMo lô đang mở gần nhất, {@code null} nếu không có
     */
    record TinhHinhChi(UUID branchId, int soLo, int soLoDaGhi, ImportBatch loDangMo) {
    }

    /**
     * Đếm gộp theo chi cho <b>cả dòng họ trong một lượt</b>.
     *
     * <p>Màn tiến độ hiện mọi chi, nên lối gọi một-chi-một-truy-vấn sẽ thành N+1 ngay từ dòng họ
     * thứ nhất có mươi chi. Chi không có lô nào vẫn có mặt trong bản đồ trả về, với
     * {@code soLo = 0} — vắng mặt và "chưa bắt đầu" là hai câu trả lời khác nhau.</p>
     */
    Map<UUID, TinhHinhChi> tongHopTheoChi(Collection<UUID> branchIds);
}
