package vn.giapha.content.domain;

import java.util.Locale;

/**
 * Loại vinh danh — khớp <b>từng ký tự</b> với ràng buộc {@code ck_honour_kind} của V17.
 *
 * <p>Đưa thành enum thay vì chuỗi tự do để một giá trị sai chính tả lộ ra lúc biên dịch, chứ không
 * lộ ra dưới dạng một transaction chết giữa chừng: Postgres huỷ mọi lệnh còn lại sau khi một
 * {@code CHECK} bị vi phạm, nên câu INSERT hỏng sẽ kéo theo cả dòng {@code audit_log} đang ghi vết
 * cho nó. Cùng lý do với {@code audit.domain.AuditAction}.</p>
 *
 * <p><b>Tên hằng giữ nguyên tiếng Việt không dấu.</b> Bốn loại này là phân loại của <i>dòng họ</i>
 * chứ không phải của phần mềm, và "đỗ đạt" không có từ tiếng Anh nào dịch đúng — {@code DEGREE}
 * mất phần khoa bảng, {@code ACHIEVEMENT} lại trùng với {@link #THANH_TICH}. Giữ nguyên tiếng Việt
 * là cách duy nhất để bốn giá trị này còn phân biệt được với nhau.</p>
 */
public enum HonourKind {

    /** <b>Đỗ đạt</b> — khoa bảng: tiến sĩ, cử nhân, thủ khoa; cả khoa cử xưa lẫn bằng cấp nay. */
    DO_DAT,

    /** <b>Chức tước</b> — phẩm hàm, chức vụ được bổ nhiệm. */
    CHUC_TUOC,

    /** <b>Thành tích</b> — giải thưởng, kỷ lục, công trình. */
    THANH_TICH,

    /** <b>Khen thưởng</b> — huân/huy chương, bằng khen, danh hiệu do một cơ quan trao. */
    KHEN_THUONG;

    /**
     * Phân giải từ chuỗi.
     *
     * @return {@code null} khi đầu vào rỗng — bên gọi hiểu là "không lọc theo loại"
     * @throws IllegalArgumentException khi chuỗi không rỗng nhưng không phải loại nào
     */
    public static HonourKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Loai vinh danh khong nam trong ck_honour_kind: "
                    + raw + "; chi nhan DO_DAT, CHUC_TUOC, THANH_TICH, KHEN_THUONG", ex);
        }
    }
}
