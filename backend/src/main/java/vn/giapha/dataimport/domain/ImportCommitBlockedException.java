package vn.giapha.dataimport.domain;

import java.util.List;
import vn.giapha.shared.exception.DomainException;

/**
 * Bước ghi <b>dừng có kiểm soát</b> trước khi viết một dòng nào vào phả.
 *
 * <h2>Khác gì với một lỗi của bộ kiểm</h2>
 * {@link ImportIssue} nói "dòng 137 sai, sửa đi rồi tải lại". Ngoại lệ này nói một câu khác hẳn:
 * <b>tệp không sai, nhưng dòng ấy chạm vào một câu hỏi mà dòng họ chưa trả lời</b> — đời của
 * dâu/rể, con nuôi từ trong họ, người mất liên lạc (kế hoạch §14, ca 2, 5 và 9). Người nhập không
 * tự sửa được bằng cách gõ lại một ô; cần Hội đồng Tộc biểu chốt một quy ước rồi cả bốn chi làm
 * theo.
 *
 * <h2>Vì sao dừng chứ không đoán</h2>
 * Vì cái sai đắt nhất của nhập liệu gia phả là loại <b>hoàn toàn hợp lệ về dữ liệu</b>: gán một
 * đời thứ sai cho một bà dâu không vi phạm ràng buộc nào, không lỗi, không cảnh báo — nó chỉ làm
 * danh xưng của cả một cành tính sai, và chỉ lộ ra khi một cụ cao niên nhìn phả đồ ba tháng sau.
 * Dừng lại mất một buổi họp; đoán sai mất một cành.
 *
 * @param issues các dòng gây chặn, đã đủ số dòng và một câu tiếng Việt để hiện lên báo cáo
 */
public class ImportCommitBlockedException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Mã nghiệp vụ chung của cả nhóm; mã chi tiết nằm ở từng {@link ImportIssue}. */
    public static final String CODE = "IMP_COMMIT_BLOCKED";

    private final transient List<ImportIssue> issues;

    public ImportCommitBlockedException(String message, List<ImportIssue> issues) {
        super(CODE, message);
        this.issues = List.copyOf(issues);
    }

    public List<ImportIssue> issues() {
        return issues;
    }
}
