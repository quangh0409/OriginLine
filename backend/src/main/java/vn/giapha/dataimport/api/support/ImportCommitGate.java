package vn.giapha.dataimport.api.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.shared.exception.DomainException;

/**
 * <b>Cổng duyệt</b>: bốn điều kiện phải đúng trước khi một lô được trao cho bước ghi.
 *
 * <h2>Vì sao cổng nằm ở máy chủ dù giao diện đã ẩn nút</h2>
 * Nút bị ẩn không phải là một phép kiểm. Giao diện ẩn nút để người dùng không bấm nhầm; cổng này
 * tồn tại để một lô <b>không thể</b> đi vào phả khi chưa đủ điều kiện, kể cả khi lời gọi đến từ
 * một tab cũ, một bản giao diện cũ, hay một dòng {@code curl}.
 *
 * <h2>Bốn mã lỗi, không phải một</h2>
 * Bốn điều kiện dẫn tới bốn hành động khác nhau của người nhập: sửa tệp rồi tải lại · đọc rồi tick
 * đã xem · mở màn đối chiếu quyết từng cặp · nộp lô mới. Một mã gộp "chưa đủ điều kiện" bắt họ tự
 * đoán mình phải làm gì.
 *
 * <h2>Thứ tự kiểm là thứ tự người nhập phải xử lý</h2>
 * Lô đã chốt → còn lỗi chặn → chưa xác nhận cảnh báo → còn cặp nghi trùng chưa quyết. Kiểm ngược
 * thứ tự này thì người nhập được bảo đi quyết mấy cặp nghi trùng của một lô mà đằng nào cũng phải
 * nộp lại.
 *
 * <h2>"Đã xác nhận" là một câu hỏi về <b>tập cảnh báo</b>, không về một dấu thời gian</h2>
 * Một lô được kiểm lại nhiều lần. Nếu lần kiểm sau sinh ra cảnh báo <b>mới</b> thì cái tick của
 * lần trước không còn nói lên điều gì — người ấy chưa đọc những dòng mới. Vì vậy cổng này hỏi
 * {@code batch.daXemHetCanhBao()} chứ không hỏi {@code warningsAcknowledgedAt != null}.
 */
@Component
public class ImportCommitGate {

    private static final Logger log = LoggerFactory.getLogger(ImportCommitGate.class);

    /**
     * @param undecidedDuplicates số cặp nghi trùng người nhập chưa quyết
     * @throws DomainException mang một trong bốn mã của {@link ImportProblemCodes}; tầng
     *         {@code shared.api.GlobalExceptionHandler} dịch thành {@code 422}
     */
    public void check(ImportBatch batch, int undecidedDuplicates) {
        if (batch.status().daChot()) {
            throw new DomainException(ImportProblemCodes.BATCH_CLOSED,
                    "Lô này đã ở trạng thái " + batch.status() + " nên không duyệt được nữa."
                            + " Hãy tải lên một lô mới thay vì sửa lô cũ.");
        }
        if (!batch.status().sanSangGhi()) {
            // Chua kiem xong, hoac kiem xong ma con loi chan -> deu roi ve mot cau hanh dong.
            throw new DomainException(ImportProblemCodes.BLOCKING_ISSUES_PRESENT,
                    "Lô đang ở trạng thái " + batch.status() + ", chưa qua bước kiểm sạch lỗi chặn."
                            + " Hãy chạy lại bộ kiểm rồi sửa những dòng được báo.");
        }
        if (batch.blockingCount() > 0) {
            throw new DomainException(ImportProblemCodes.BLOCKING_ISSUES_PRESENT,
                    "Còn " + batch.blockingCount() + " lỗi chặn phải sửa trong tệp Excel rồi tải"
                            + " lại. Lỗi chặn là mâu thuẫn nội tại của dữ liệu, không bỏ qua được.");
        }
        if (!batch.daXemHetCanhBao()) {
            // KHONG hoi "warningsAcknowledgedAt co null khong". Hoi vay thi mot xac nhan tu lan
            // kiem TRUOC van con hieu luc sau khi lan kiem sau sinh ra canh bao MOI — tuc la nguoi
            // duyet dang xac nhan nhung dong ho chua tung nhin thay, va he thong ghi dieu do nhan
            // danh ho. daXemHetCanhBao() so van tay hai tap canh bao, xem ImportBatch.
            boolean chuaTickLanNao = batch.warningsAcknowledgedAt() == null;
            throw new DomainException(ImportProblemCodes.WARNINGS_NOT_ACKNOWLEDGED,
                    chuaTickLanNao
                            ? "Còn " + batch.warningCount() + " điều nên xem lại. Hãy đọc rồi xác"
                                    + " nhận đã xem — cảnh báo không chặn, nhưng không được bỏ qua"
                                    + " trong im lặng."
                            : "Lần kiểm gần nhất sinh ra những điều nên xem lại mà lần xác nhận"
                                    + " trước chưa hề có. Xác nhận cũ vì vậy không còn hiệu lực:"
                                    + " hãy đọc lại " + batch.warningCount() + " mục rồi xác nhận"
                                    + " một lần nữa.");
        }
        if (undecidedDuplicates > 0) {
            throw new DomainException(ImportProblemCodes.DUPLICATES_UNDECIDED,
                    "Còn " + undecidedDuplicates + " cặp nghi trùng chưa ai quyết. Máy chỉ nghi"
                            + " ngờ; hợp nhất hay không là quyết định của người, và gộp nhầm hai"
                            + " người thì cả hai nhánh con cháu treo vào một node sai.");
        }
        // Bat bien cuoi cung, doc tu chinh aggregate: neu bon phep kiem tren dung ma cau nay sai
        // thi mot trong hai noi da lech, va ta muon biet ngay chu khong muon ghi vao pha.
        if (!batch.coTheDuyet()) {
            log.error("Cong duyet va ImportBatch.coTheDuyet() khong dong y ve lo {}", batch.id());
            throw new DomainException(ImportProblemCodes.BLOCKING_ISSUES_PRESENT,
                    "Lô chưa đủ điều kiện ghi vào phả.");
        }
    }
}
