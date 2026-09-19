package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.AcceptedInvitation;
import vn.giapha.membership.domain.AppUser;

/**
 * Kết quả của việc nhận lời mời.
 *
 * <p>{@link #status()} trả về {@code ACTIVE} và {@link #personId()} khác {@code null} <b>ngay trong
 * phản hồi này</b> — đó là bằng chứng đọc được từ phía client rằng không có bước chờ duyệt nào.
 * Người mời đã chỉ đích danh người mình mời; hệ thống không hỏi lại một câu đã có đáp án.</p>
 *
 * <h2>{@link #setPasswordUrl()} — liên kết một lần, và nó VẮNG thì cũng có nghĩa</h2>
 * Trường này có mặt khi tài khoản <b>chưa có mật khẩu</b>: người được mời vừa được lập tài khoản
 * Keycloak và cần tự đặt mật khẩu. Nó <b>vắng</b> khi tài khoản đã có mật khẩu — người ấy đăng nhập
 * như bình thường.
 *
 * <p>Đây không phải "một trường đôi khi rỗng". Phát liên kết đặt mật khẩu cho một tài khoản
 * <i>đã</i> có mật khẩu là mở một lối đổi mật khẩu cho bất kỳ ai cầm mã mời và đoán đúng email của
 * một thành viên cũ — nên sự vắng mặt ở đây là một phép chặn, không phải một thiếu sót. Giao diện
 * rẽ hai nhánh: có thì tới màn đặt mật khẩu, không thì tới màn đăng nhập. Không nhánh nào chuyển
 * hướng tới {@code null}.</p>
 *
 * <p>Liên kết có hạn ngắn ({@link #setPasswordExpiresAt()}, mặc định nửa giờ) và <b>chết ngay khi
 * mật khẩu được đặt</b> — tính một lần ấy được canh bằng chính trạng thái ở Keycloak ("tài khoản
 * này đã có mật khẩu chưa"), không bằng một cờ trong cơ sở dữ liệu có thể lệch khỏi sự thật.</p>
 */
@Schema(description = "Tài khoản sau khi nhận lời mời — đã gắn nhân khẩu, không qua chờ duyệt")
public record AcceptedInvitationDto(

        @Schema(description = "Tài khoản của người vừa nhận lời mời")
        UUID appUserId,

        @Schema(description = "Nhân khẩu đã được gắn; dùng cho màn hạ cánh")
        UUID personId,

        @Schema(description = "Luôn là ACTIVE khi nhận thành công", example = "ACTIVE")
        String status,

        @Schema(description = "Liên kết một lần để tự đặt mật khẩu. VẮNG khi tài khoản đã có mật"
                + " khẩu — lúc ấy đưa người dùng tới màn đăng nhập.")
        String setPasswordUrl,

        @Schema(description = "Hạn của setPasswordUrl; vắng cùng lúc với nó")
        Instant setPasswordExpiresAt) {

    public static AcceptedInvitationDto from(AcceptedInvitation accepted) {
        AppUser user = accepted.user();
        return new AcceptedInvitationDto(user.id(), user.personId(), user.status().name(),
                accepted.setPasswordLink().map(link -> link.url()).orElse(null),
                accepted.setPasswordLink().map(link -> link.expiresAt()).orElse(null));
    }
}
