package vn.giapha.events.application;

/**
 * Đã có một lượt sinh lịch nhắc đang chạy trên chính instance này.
 *
 * <p>Không phải lỗi dữ liệu, cũng không phải lỗi hệ thống — chỉ là "đợi lượt trước xong đã". Vì vậy
 * nó <b>không</b> kế thừa {@code DomainException} (sẽ thành 422 "vi phạm quy tắc nghiệp vụ", sai
 * ngữ nghĩa); tầng api quy nó thành <b>409 Conflict</b>.</p>
 *
 * <p><b>Vì sao cần chốt này khi việc ghi vốn đã idempotent:</b> chống trùng nằm ở chỉ mục duy nhất
 * của {@code reminder_job} nên chạy hai lượt song song không sinh dữ liệu rác. Cái nó gây ra là
 * <i>tải</i>: một lượt quét toàn bộ bảng {@code event} của dòng họ lớn, nhân với số lần quản trị
 * viên sốt ruột bấm lại. Chốt ở đây là chốt trong tiến trình, không phải khoá phân tán — nhiều
 * instance vẫn có thể chạy đồng thời, và điều đó vẫn an toàn.</p>
 */
public class ReminderGenerationBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Mã lỗi ổn định cho frontend, dạng {@code CONTEXT.RULE}. */
    public static final String CODE = "REMINDER_GENERATION_IN_PROGRESS";

    public ReminderGenerationBusyException(String message) {
        super(message);
    }
}
