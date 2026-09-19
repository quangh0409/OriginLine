package vn.giapha.membership.application.command;

/**
 * "Đúng là tôi" — lệnh nhận lời mời.
 *
 * <h2>Hai lối vào, phân biệt bằng việc CÓ TOKEN hay không, chứ không bằng một cờ</h2>
 * <ul>
 *   <li><b>Chưa có tài khoản</b> — lối thường gặp, và là lý do luồng mời tồn tại. Người nhận tự
 *       khai {@link #email()}; hệ thống tạo tài khoản Keycloak cho họ rồi trả về một liên kết một
 *       lần để họ đặt mật khẩu.</li>
 *   <li><b>Đã đăng nhập</b> — người đã vào bằng Google/Zalo nhưng chưa được ghép vào cây. Token
 *       nói họ là ai, nên {@link #email()} bị <b>bỏ qua</b>: tin vào một email gõ tay trong khi
 *       trong tay đã có một token đã ký là tự hạ cấp bằng chứng.</li>
 * </ul>
 *
 * <p>Không có cờ "tạo tài khoản hay không" trong lệnh này. Một cờ như thế sẽ cho phép client yêu
 * cầu tạo tài khoản cho một email tuỳ ý <i>trong khi</i> đang cầm token của người khác — một lối
 * tạo tài khoản mà không ai kiểm.</p>
 *
 * @param code        mã mời thô, chưa chuẩn hoá
 * @param email       địa chỉ thư người nhận tự khai; chỉ dùng khi <b>không</b> có token
 * @param displayName tên hiển thị tuỳ chọn cho tài khoản mới
 * @param clientId    định danh người gọi cho bộ đếm giới hạn tần suất (băm trước khi chạm CSDL)
 */
public record AcceptInvitationCommand(String code, String email, String displayName,
                                      String clientId) {

    /** Người đã có token: danh tính lấy từ token, không hỏi email. */
    public static AcceptInvitationCommand byCurrentUser(String code, String clientId) {
        return new AcceptInvitationCommand(code, null, null, clientId);
    }

    /** Người chưa có tài khoản: tự khai email, hệ thống lập tài khoản cho. */
    public static AcceptInvitationCommand byEmail(String code, String email, String displayName,
                                                  String clientId) {
        return new AcceptInvitationCommand(code, email, displayName, clientId);
    }
}
