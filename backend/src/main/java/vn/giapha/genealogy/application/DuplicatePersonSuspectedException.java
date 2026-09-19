package vn.giapha.genealogy.application;

import java.util.List;

/**
 * Hồ sơ đang định nhập <b>nghi trùng</b> với một nhân khẩu đã có — HTTP <b>409</b>, mã
 * {@code DUPLICATE_PERSON_SUSPECTED}.
 *
 * <p>Cùng hình dạng nghiệp vụ với {@link TabooNameConflictException}: <b>cảnh báo, không phải lệnh
 * cấm</b>. Lần gọi đầu không ghi bản ghi nào và trả về danh sách nhân khẩu bị nghi để người nhập
 * liệu tự đối chiếu; lần gọi sau kèm {@code confirmDuplicateOverride = true} thì ghi bình thường và
 * lưu việc ghi đè vào {@code audit_log}.</p>
 *
 * <p><b>Máy nghi ngờ, người quyết định.</b> Hệ thống không bao giờ tự gộp hai hồ sơ và cũng không
 * bao giờ từ chối vĩnh viễn: gộp nhầm hai người là loại lỗi rất khó gỡ vì cả hai nhánh con cháu đều
 * đã treo vào node sai.</p>
 *
 * <h2>Câu {@code detail} soạn bằng SỐ LIỆU và NHÃN, không bằng danh tính</h2>
 * Bộ dò quét <b>toàn dòng họ</b>, không chỉ chi của người gọi, và nó không biết người gọi là ai.
 * Hồ sơ bị nghi hoàn toàn có thể là một người <b>còn sống</b> mà người đang thêm nhân khẩu không
 * được xem tên huý, năm sinh hay nguyên quán ({@code PersonVisibility#ungroupedFieldsVisible()}
 * chỉ mở cho người đã khuất, chính chủ và Hội đồng Tộc biểu/Admin). Câu này lại đi ra thân lỗi
 * <b>và</b> vào log, nên nối một giá trị đọc từ phả vào đây là phát dữ liệu ấy cho đúng người
 * không được xem, ở một kênh mà bộ lọc phân tầng riêng tư không canh.
 *
 * <p>Ranh giới đã chốt, giống hệt đường nhập liệu hàng loạt
 * ({@code dataimport.application.rule.SuspectDuplicateRule}): <b>được phép nói trường nào của
 * chính người gọi vừa nhập đã khớp, không bao giờ nói một giá trị đọc từ phả</b>. Vì vậy câu này
 * giữ số ứng viên, điểm cao nhất, nhãn tín hiệu và bước tiếp theo — bỏ tên, đời thứ và chi.</p>
 *
 * <p><b>Vì sao kế thừa {@link GenealogyConflictException}:</b> để đi đúng nhánh 409 của
 * {@code GenealogyExceptionHandler} sẵn có. Tầng api nay có thêm một {@code @ExceptionHandler}
 * riêng cho đúng lớp này — Spring chọn handler sát kiểu nhất — nên thân lỗi mang
 * {@code overridable: true}, {@code overrideField: "confirmDuplicateOverride"} và
 * {@code conflicts[]} dựng từ {@link #matches()}, khớp với {@code ConflictProblem} của hợp đồng.
 * Tầng api chỉ lấy <b>khoá và tín hiệu</b> ra khỏi {@link DuplicateMatch}; các trường còn lại của
 * bản ghi ấy là dữ liệu đọc từ phả và ở lại trong tiến trình.</p>
 */
public class DuplicatePersonSuspectedException extends GenealogyConflictException {

    private static final long serialVersionUID = 1L;

    private final transient List<DuplicateMatch> matches;

    public DuplicatePersonSuspectedException(List<DuplicateMatch> matches) {
        super(GenealogyProblemCodes.DUPLICATE_PERSON_SUSPECTED, buildMessage(matches));
        this.matches = matches == null ? List.of() : List.copyOf(matches);
    }

    /**
     * Danh sách nhân khẩu bị nghi, sắp giảm dần theo điểm.
     *
     * <p><b>Chứa dữ liệu đọc từ phả</b> ({@code displayName}, {@code generation}, {@code branchId},
     * {@code matchedName}) và vì thế <b>không được tuần tự hoá nguyên vẹn ra ngoài tiến trình</b>.
     * Tầng api chỉ lấy {@code personId}/{@code ref}/{@code score}/{@code signals}/{@code hint}.</p>
     */
    public List<DuplicateMatch> matches() {
        return matches;
    }

    /**
     * Câu mô tả cho {@code detail} của Problem Details — và cũng là thứ đi vào log.
     *
     * <p>Không tên, không đời thứ, không chi. Xem javadoc lớp: người đọc câu này có thể không có
     * quyền biết hồ sơ bị nghi là ai. Thay vào đó nói <b>vì sao nghi</b> (nhãn tín hiệu, lấy từ
     * {@link DuplicateMatch#hint()} — hợp đồng "chỉ nhãn, không giá trị trường" của trường đó được
     * ghim ở {@code DuplicateScorerTest}) và <b>phải làm gì tiếp</b>.</p>
     */
    private static String buildMessage(List<DuplicateMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return "Nghi trung voi mot nhan khau da co trong gia pha";
        }
        DuplicateMatch manhNhat = matches.get(0);
        String tinHieu = manhNhat.hint() == null || manhNhat.hint().isBlank()
                ? "" : ", khop o: " + manhNhat.hint();
        return "Nghi trung voi " + matches.size() + " ho so da co trong gia pha - diem cao nhat "
                + manhNhat.score() + tinHieu + ". Vi ly do rieng tu, than loi khong neu danh tinh:"
                + " mo tung ho so theo personId de xem phan minh duoc phep xem."
                + " Xac nhan neu day la nguoi khac.";
    }
}
