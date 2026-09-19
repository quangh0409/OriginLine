package vn.giapha.genealogy.application;

/**
 * <b>Tóm tắt</b> tầng dữ liệu mà người gọi hiện tại chạm tới được cho một hồ sơ (BA v2 §10, Nghị
 * định 13/2023).
 *
 * <p>Từ {@code V8}, quyết định thật <b>không</b> nằm ở đây nữa: nó nằm ở
 * {@link PersonVisibility#allows} với từng {@code PrivacyFieldGroup} và mức
 * {@code ShareScope} do chủ thể chọn. Giá trị này chỉ đi ra
 * {@code PersonAccessMeta.visibleTier} để giao diện biết đại khái mình đang ở đâu mà bật/tắt
 * affordance — nó <b>không</b> tiết lộ trường nào đang bị giấu: trường bị ẩn và trường không có dữ
 * liệu đều vắng mặt như nhau, và đó là chủ ý.</p>
 *
 * <h2>Vì sao {@code atLeast()} đã bị xoá</h2>
 * Phương thức cũ trả {@code true} cho <b>mọi</b> so sánh khi tier là {@link #PUBLIC}, mà người đã
 * khuất luôn ở {@code PUBLIC}. Hệ quả: {@code tier.atLeast(T3)} — cách viết tự nhiên nhất của câu
 * hỏi "người này có được xem dữ liệu Tầng 3 không" — trả {@code true} cho cả Khách vãng lai đang
 * xem hồ sơ một cụ tổ, và khối liên hệ trong hồ sơ đó thực chất là số điện thoại của người thân
 * đang sống. Bẫy chỉ tồn tại được vì có một phương thức mời gọi dùng sai; xoá phương thức là cách
 * duy nhất khiến nó không tái xuất hiện. <b>Đừng thêm lại.</b> Muốn hỏi "được xem nhóm trường nào"
 * thì hỏi {@link PersonVisibility}.
 */
public enum VisibleTier {

    /** Người đã khuất — công khai theo mục đích gia phả, kể cả với Khách. */
    PUBLIC,

    /** Tên, đời/vai vế, quan hệ lõi. Thành viên đã đăng nhập; Khách không thấy gì. */
    T1,

    /** Chủ thể đã mở ít nhất một trong hai nhóm "nghề nghiệp" / "nơi ở cấp tỉnh" cho người gọi. */
    T2,

    /** Chủ thể đã mở ít nhất một nhóm nhạy cảm (địa chỉ đầy đủ · liên hệ · ngày sinh &amp; ảnh). */
    T3
}
