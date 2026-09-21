package vn.giapha.media.domain;

import java.util.Locale;

/**
 * Lý do báo gỡ một tệp đính kèm — năm câu mà người trong họ thật sự nói.
 *
 * <p>Danh sách đóng chứ không phải ô chữ tự do, vì người duyệt cần <b>sắp xếp được hàng đợi</b>:
 * một đơn {@link #RIENG_TU} phải được xử trước một đơn {@link #BAN_QUYEN}. {@link #KHAC} là van xả
 * — và đúng vì thế nó bắt buộc phải kèm lời mô tả, xem {@code MediaReport}.</p>
 */
public enum ReportReason {

    /** Ảnh lộ thông tin riêng tư của một người (mặt trẻ nhỏ, số điện thoại trên giấy tờ…). */
    RIENG_TU,

    /** Chú thích/bài viết gán tấm ảnh cho nhầm người trong họ. */
    SAI_NGUOI,

    /** Nội dung không phù hợp với trang của dòng họ. */
    KHONG_PHU_HOP,

    /** Ảnh/video của người khác, đăng lại không xin phép. */
    BAN_QUYEN,

    /** Khác — <b>bắt buộc</b> kèm lời mô tả. */
    KHAC;

    public static ReportReason of(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
