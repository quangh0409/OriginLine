package vn.giapha.dataimport.domain;

/**
 * Ba câu trả lời của <b>người</b> cho một cặp nghi trùng, cộng trạng thái "chưa ai trả lời".
 *
 * <h2>Máy nghi ngờ, người quyết định</h2>
 * Không có ngưỡng điểm nào dẫn tới việc tự gộp. Gộp nhầm hai người là hợp nhất hai nhánh con cháu
 * vào một node sai, và sau ba mươi ngày thì không hoàn tác được nữa.
 *
 * <h2>{@link #DEFERRED} là giá trị quan trọng nhất trong ba, và nó <b>vẫn chặn</b></h2>
 * Ép người đối chiếu chọn nhị phân khi họ chưa chắc là cách chắc chắn nhất để nhận về dữ liệu sai:
 * người ta bấm đại một cái để đi tiếp, và cái bấm đại ấy trở thành sự thật trong phả. "Hoãn" cho
 * họ một lối thoát trung thực.
 *
 * <p>Nhưng nếu "hoãn" mở khoá nút duyệt thì nó lập tức biến thành nút <i>"cho tôi qua"</i> và cả
 * cơ chế dò trùng thành trang trí — ai cũng bấm hoãn ba mươi cặp rồi ghi. Vì vậy
 * {@link #chuaQuyet()} trả {@code true} cho <b>cả</b> {@code PENDING} lẫn {@code DEFERRED}.</p>
 */
public enum DuplicateDecision {

    /** Chưa ai nhìn tới cặp này. */
    PENDING,

    /** <b>Đây là cùng một người.</b> Nghĩa của nó khác hẳn nhau giữa hai loại cặp — xem
     *  {@link DuplicateMergePlan}. */
    MERGED,

    /** Hai người khác nhau. Cả hai cùng vào phả, không ai bị bỏ. */
    DISTINCT,

    /** Chưa chắc, để lại sau. <b>Vẫn chặn cổng duyệt</b> — xem javadoc lớp. */
    DEFERRED;

    /**
     * Cặp này còn chặn cổng duyệt hay không.
     *
     * <p>Đọc kỹ: "đã bấm một cái nút" và "đã quyết" là hai chuyện khác nhau. {@code DEFERRED} là
     * cái thứ nhất mà không phải cái thứ hai.</p>
     */
    public boolean chuaQuyet() {
        return this == PENDING || this == DEFERRED;
    }

    /** Quyết định này có chủ và có mốc thời gian — mọi giá trị trừ {@link #PENDING}. */
    public boolean coChu() {
        return this != PENDING;
    }

    /**
     * Đọc giá trị do client gửi lên.
     *
     * <p>{@code PENDING} <b>không</b> nhận được: rút lại một lời khai đã ghi không phải là xoá nó
     * đi, và chưa ai cần nghiệp vụ ấy. Muốn đổi ý thì gửi một trong ba giá trị kia.</p>
     *
     * @throws IllegalArgumentException giá trị không hợp lệ, hoặc là {@code PENDING}
     */
    public static DuplicateDecision tuYeuCau(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Thiếu quyết định. Phải là một trong: MERGED, DISTINCT, DEFERRED.");
        }
        DuplicateDecision parsed;
        try {
            parsed = valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Quyết định không hợp lệ: \"" + value
                    + "\". Phải là một trong: MERGED, DISTINCT, DEFERRED.", ex);
        }
        if (parsed == PENDING) {
            throw new IllegalArgumentException(
                    "PENDING là trạng thái ban đầu, không phải một quyết định gửi lên được.");
        }
        return parsed;
    }
}
