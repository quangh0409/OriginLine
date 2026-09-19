package vn.giapha.dataimport.api.support;

/**
 * Hai ngưỡng của thang điểm nghi trùng, <b>do backend công bố</b>.
 *
 * <h2>Vì sao hai con số này phải đi ra khỏi máy chủ chứ không nằm trong mã giao diện</h2>
 * Thang điểm là dữ liệu hiệu chỉnh của bộ dò trùng: nó đã được đo trên một dòng họ mô phỏng 1.506
 * người và sẽ còn được chỉnh. Một bản sao ghi cứng trong giao diện chắc chắn sẽ trôi, và triệu
 * chứng của nó rất khó truy: giao diện chọn sẵn "hợp nhất" cho một cặp mà máy chủ coi là chưa đáng
 * nghi, hoặc ngược lại — người đối chiếu nhìn thấy hai câu trả lời khác nhau cho cùng một cặp.
 *
 * <h2>Ba con số đang trôi nổi, và con số nào đúng</h2>
 * <ul>
 *   <li><b>60</b> — {@code plan/02-nhap-lieu §6.4}. <b>Sai.</b> Kế hoạch viết trước khi bộ dò được
 *       hiệu chỉnh.</li>
 *   <li><b>70</b> — {@code DuplicateScorer.NGUONG_NGHI_TRUNG}. <b>Đúng.</b> Đây là ngưỡng thật đang
 *       chạy: dưới nó bộ dò không sinh cảnh báo nào, nên mọi con số nhỏ hơn 70 mà giao diện dùng
 *       đều mô tả một trạng thái không tồn tại.</li>
 *   <li><b>85</b> — {@code plan §6.4}, ngưỡng giao diện <i>chọn sẵn</i> "hợp nhất". Không có bản
 *       sao nào ở backend vì nó là quyết định về giao diện, không phải về chấm điểm — nhưng vẫn
 *       phải do backend công bố để hai đầu không trôi.</li>
 * </ul>
 *
 * <h2>Không có ngưỡng nào tự gộp người</h2>
 * {@link #SUSPECT_THRESHOLD} chỉ quyết định <i>có hỏi hay không</i>;
 * {@link #PRESELECT_MERGE_THRESHOLD} chỉ quyết định <i>ô nào được tick sẵn</i>. Quyết định hợp nhất
 * luôn là của người. Gộp nhầm hai người là hợp nhất hai nhánh con cháu vào một node sai.
 *
 * <h2>Chống trôi bằng test, không bằng lời hứa</h2>
 * {@code ImportDuplicatePolicyTest} khẳng định {@link #SUSPECT_THRESHOLD} bằng đúng
 * {@code DuplicateScorer.NGUONG_NGHI_TRUNG}. Sửa một trong hai chỗ mà quên chỗ kia là một bài test
 * đỏ, không phải một lỗi im lặng. (Không tham chiếu thẳng hằng số ấy được ở mã chính vì
 * {@code genealogy.application} chưa được công bố làm {@code @NamedInterface}; test thì gọi được,
 * và đó chính là chỗ nên đặt cái chốt này.)
 */
public final class ImportDuplicatePolicy {

    /** Điểm tối thiểu để bộ dò sinh cảnh báo {@code IMP_SUSPECT_DUPLICATE}. */
    public static final int SUSPECT_THRESHOLD = 70;

    /** Điểm từ đó giao diện tick sẵn "hợp nhất" — vẫn phải người bấm xác nhận. */
    public static final int PRESELECT_MERGE_THRESHOLD = 85;

    /** Máy không bao giờ tự gộp, ở bất kỳ điểm số nào. */
    public static final boolean AUTO_MERGE = false;

    private ImportDuplicatePolicy() {
    }
}
