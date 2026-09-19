package vn.giapha.membership.application;

import java.util.Optional;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.port.SetPasswordLink;

/**
 * Kết quả của việc nhận lời mời: tài khoản đã ghép với nhân khẩu, và — khi cần — liên kết một lần
 * để người ấy tự đặt mật khẩu.
 *
 * <h2>{@link #setPasswordLink()} là {@link Optional}, và nó rỗng có NGHĨA</h2>
 * Rỗng nghĩa là tài khoản <b>đã có mật khẩu</b>: người ấy đăng nhập như bình thường. Đây không phải
 * một trường "chưa làm xong" — trả một liên kết đặt mật khẩu cho một tài khoản đã có mật khẩu là mở
 * lối đổi mật khẩu cho bất kỳ ai cầm mã mời và đoán đúng email của một thành viên cũ.
 *
 * <p>Giao diện vì thế rẽ hai nhánh: có liên kết thì đưa người dùng tới màn đặt mật khẩu; không có
 * thì đưa tới màn đăng nhập. Không nhánh nào dẫn tới {@code null}.</p>
 */
public record AcceptedInvitation(AppUser user, Optional<SetPasswordLink> setPasswordLink) {

    /** Người đã có tài khoản và đã có mật khẩu — chỉ cần đăng nhập. */
    public static AcceptedInvitation withoutLink(AppUser user) {
        return new AcceptedInvitation(user, Optional.empty());
    }
}
