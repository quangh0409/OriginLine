package vn.giapha.dataimport.application.rule;

/**
 * Một luật của bộ kiểm.
 *
 * <h2>Một luật, một câu hỏi</h2>
 * Mỗi hiện thực trả lời đúng một nhóm câu hỏi liên quan chặt với nhau và gom lỗi vào
 * {@link ValidationContext}. Không luật nào được sửa dữ liệu: <b>máy không tự sửa con số của
 * người</b>. Không luật nào được ném ngoại lệ khi dữ liệu sai — dữ liệu sai là đầu ra bình thường
 * của bộ kiểm, và một ngoại lệ giữa chừng làm mất toàn bộ các lỗi còn lại, tức là người nhập sửa
 * xong dòng 12 lại phát hiện dòng 13, rồi dòng 14, mỗi lần một vòng tải lên.
 */
public interface ImportRule {

    void apply(ValidationContext ctx);

    /** Tên ngắn để ghi log khi một luật chạy chậm. */
    default String ten() {
        return getClass().getSimpleName();
    }
}
