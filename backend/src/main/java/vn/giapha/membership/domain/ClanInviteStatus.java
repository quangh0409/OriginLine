package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Trạng thái lưu trữ của một mã mời dòng họ — khớp {@code ck_clan_invite_status} của V16.
 *
 * <p><b>Cố ý chỉ có hai giá trị.</b> "Hết hạn" và "hết lượt" <i>không</i> nằm ở đây: cả hai là
 * phép so sánh (với đồng hồ, với {@code use_count}), không phải một dòng dữ liệu. Nếu chúng là
 * trạng thái lưu trữ thì phải có một job đi lật cờ, và cho tới khi job ấy chạy thì mọi mã quá hạn
 * vẫn dùng được — một lỗ hổng có lịch chạy. Xem {@link ClanInviteUsability}.</p>
 */
public enum ClanInviteStatus {

    /** Đã phát, chưa bị thu hồi. Còn dùng được hay không thì hỏi đồng hồ và bộ đếm. */
    ACTIVE,

    /**
     * Bị Hội đồng thu hồi — <b>chốt 2 của §1.2</b>.
     *
     * <p>Thu hồi <b>không</b> động tới người đã vào: họ đã có tài khoản, và tài khoản ấy không
     * treo vào mã. Đúng như yêu cầu "đóng lại ngay mà không ảnh hưởng người đã vào".</p>
     */
    REVOKED;

    public static ClanInviteStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
