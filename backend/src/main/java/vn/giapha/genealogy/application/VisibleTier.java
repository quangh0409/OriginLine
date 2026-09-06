package vn.giapha.genealogy.application;

/**
 * Tầng dữ liệu mà <b>người gọi hiện tại</b> nhận được cho một hồ sơ (BA v2 §10, Nghị định
 * 13/2023).
 *
 * <p>Giá trị này đi ra {@code PersonAccessMeta.visibleTier} để giao diện biết mình đang ở đâu mà
 * bật/tắt affordance. Nó <b>không</b> tiết lộ trường nào đang bị giấu: trường bị ẩn và trường
 * không có dữ liệu đều vắng mặt như nhau, và đó là chủ ý.</p>
 */
public enum VisibleTier {

    /** Người đã khuất — công khai theo mục đích gia phả, kể cả với Khách. */
    PUBLIC,

    /** Tên, đời/vai vế, quan hệ lõi. Thành viên đã đăng nhập; Khách không thấy gì. */
    T1,

    /** Thêm năm sinh, nghề nghiệp, nơi ở cấp tỉnh. Cùng chi/ngành hoặc có phạm vi. */
    T2,

    /** Thêm SĐT, email, địa chỉ đầy đủ, ngày sinh đầy đủ, ảnh. Chính chủ + Admin + opt-in. */
    T3;

    /** {@code true} nếu tầng này bao hàm tầng {@code other} (PUBLIC bao hàm mọi tầng phả hệ). */
    public boolean atLeast(VisibleTier other) {
        if (this == PUBLIC) {
            return true;
        }
        return other != PUBLIC && ordinal() >= other.ordinal();
    }
}
