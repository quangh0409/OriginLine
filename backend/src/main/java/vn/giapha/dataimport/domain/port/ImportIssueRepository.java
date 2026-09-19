package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.UUID;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;

/** Kho lỗi và cảnh báo của một lô. */
public interface ImportIssueRepository {

    /**
     * Xoá sạch rồi ghi lại toàn bộ.
     *
     * <p><b>Không tích luỹ qua các lần kiểm.</b> Nếu tích luỹ thì người nhập sửa xong, chạy lại,
     * và vẫn thấy y nguyên danh sách cũ — họ sẽ kết luận bộ kiểm hỏng và thôi không đọc nữa.</p>
     */
    void replaceAll(UUID batchId, List<ImportIssue> issues);

    /** Đã sắp theo sheet, row_no, code để hai lần chạy so sánh được từng dòng. */
    List<ImportIssue> byBatch(UUID batchId);

    /**
     * Một lỗi/cảnh báo <b>đã lưu</b>: nội dung, cộng khoá của dòng trong {@code import_issue}.
     *
     * <h2>Vì sao khoá nằm ở đây mà không nằm trong {@link ImportIssue}</h2>
     * {@code ImportIssue} là thứ <b>bộ luật sinh ra</b>, và lúc sinh ra nó chưa có khoá — mười ba
     * luật đều dựng nó bằng các hàm dựng tĩnh. Nhét một trường {@code id} luôn null vào đó chỉ để
     * tầng đọc dùng được là bắt cả bộ luật mang theo một trường vô nghĩa với chúng.
     *
     * <p>Khoá này quan trọng với giao diện: không có nó, bảng lỗi phải tự ghép
     * {@code (sheet, rowNo, code, field)} làm khoá React — mà bộ khoá ấy <b>không duy nhất</b>
     * (hai luật khác nhau có thể cùng trỏ vào một ô), nên danh sách sẽ nhảy chỗ khi kiểm lại.</p>
     */
    record Luu(UUID id, ImportIssue issue) {
    }

    /** Như {@link #byBatch}, nhưng mang theo khoá dòng. Cùng thứ tự tất định. */
    List<Luu> byBatchWithId(UUID batchId);

    /**
     * Đếm số vấn đề mang một mã nhất định trong một lô.
     *
     * <p>Đếm ở CSDL chứ không nạp cả danh sách rồi lọc: màn tiến độ hỏi con số này cho mọi chi
     * cùng lúc, và nạp toàn bộ lỗi của bốn lô chỉ để đếm một mã là lãng phí có hệ thống.</p>
     */
    int demTheoMa(UUID batchId, IssueCode code);
}
