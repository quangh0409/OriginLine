package vn.giapha.events.domain;

/**
 * Lý do ngày giỗ năm nay <b>lệch</b> khỏi ngày âm chép trong gia phả.
 *
 * <p>Giá trị này đi kèm mọi lần quy đổi để thông báo có thể nói thật với người trong họ ("năm nay
 * tháng thiếu, giỗ lùi về 29") thay vì đưa ra một ngày khác với sổ mà không giải thích. Ngày giỗ sai
 * lệch âm thầm là loại lỗi không bao giờ tự lộ ra — người ta chỉ phát hiện khi đã cúng nhầm ngày.</p>
 */
public enum OccurrenceAdjustment {

    /** Ngày âm tồn tại đúng như chép trong gia phả. */
    EXACT(null),

    /**
     * Ngày 30 của một tháng thiếu (tháng chỉ có 29 ngày) — lùi về ngày 29.
     *
     * <p>Đây đúng là tập quán: giỗ ghi ngày 30 mà năm ấy tháng thiếu thì làm ngày 29.</p>
     */
    SHORT_MONTH("Nam nay thang thieu (khong co ngay 30), gio lui ve ngay 29"),

    /**
     * Sự kiện chép vào <b>tháng nhuận</b> nhưng năm nay tháng ấy không nhuận — dùng tháng thường
     * cùng số. Tháng thường luôn đứng <i>trước</i> tháng nhuận nên vẫn giữ nguyên tắc "không giỗ
     * muộn".
     */
    LEAP_MONTH_ABSENT("Nam nay khong nhuan thang nay, gio tinh theo thang thuong"),

    /** Phải lùi thêm ngày mới quy đổi được — trường hợp hiếm, luôn kèm log cảnh báo. */
    SHIFTED_EARLIER("Ngay am khong quy doi duoc, gio lui ve ngay gan nhat truoc do");

    private final String note;

    OccurrenceAdjustment(String note) {
        this.note = note;
    }

    /** Ghi chú tiếng Việt không dấu để chèn vào nội dung thông báo; {@code null} khi không lệch. */
    public String note() {
        return note;
    }

    public boolean isAdjusted() {
        return this != EXACT;
    }
}
