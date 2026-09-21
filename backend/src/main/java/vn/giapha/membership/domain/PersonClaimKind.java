package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Hai loại đơn của cùng một câu hỏi "tôi là ai trong dòng họ này".
 *
 * <p>Chúng dùng chung một bảng và một máy trạng thái vì phần giống nhau là phần lớn: một tài khoản
 * chưa ghép · một số điện thoại · vài dòng tự giới thiệu · một chi đích quyết định ai duyệt ·
 * duyệt/từ chối/rút. Tách làm hai bảng là chép lại máy trạng thái và phép kiểm phạm vi hai lần rồi
 * để chúng lệch nhau.</p>
 */
public enum PersonClaimKind {

    /**
     * "Tôi là người này trong phả" — trỏ vào một nhân khẩu <b>đã có</b>.
     *
     * <p>Duyệt thì chỉ <i>gắn</i> tài khoản vào nhân khẩu ấy; không có nhân khẩu nào được tạo.</p>
     */
    EXISTING,

    /**
     * "Tôi chưa có trong phả" — con dâu mới về, cháu mới sinh, nhánh ở xa nhiều đời.
     *
     * <p><b>Đây là lối GHI VÀO PHẢ, không phải một biểu mẫu liên hệ</b> (design 07 §1.5): nó cho
     * một người <i>chưa được duyệt</i> khởi tạo việc thêm người vào gia phả. Năm ràng buộc đi kèm
     * nằm ở javadoc của {@code PersonClaimService}.</p>
     */
    NEW_PERSON;

    /** {@code true} nếu loại này <b>tạo</b> một nhân khẩu mới khi được duyệt. */
    public boolean taoNhanKhauKhiDuyet() {
        return this == NEW_PERSON;
    }

    public static PersonClaimKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Loai don khong duoc rong");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
