package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân yêu cầu của {@code POST /api/v1/invitations/accept} — "Đúng là tôi".
 *
 * <h2>Vì sao đây là một kiểu RIÊNG, không phải {@link RedeemInvitationRequest}</h2>
 * Ba thao tác dùng mã có <b>ba</b> nhu cầu khác nhau: xem và từ chối chỉ cần mã, còn nhận thì cần
 * thêm địa chỉ thư để lập tài khoản. Nhét {@code email} vào kiểu dùng chung sẽ làm
 * {@code /lookup} và {@code /decline} nhận một trường chúng không bao giờ đọc — và một trường như
 * thế sớm muộn sẽ có người gửi lên, rồi có người tưởng nó được dùng.
 *
 * <h2>{@link #email()} có thể vắng, và lúc ấy token là nguồn danh tính</h2>
 * Người đã đăng nhập (ví dụ vào bằng Google trước rồi mới nhận được lời mời) không cần khai email:
 * token đã nói họ là ai, và tin một email gõ tay hơn một token đã ký là tự hạ cấp bằng chứng. Người
 * <i>chưa</i> có tài khoản thì bắt buộc phải khai — đó là tên đăng nhập tương lai của họ.
 *
 * <p>Mã mời vẫn đi trong <b>thân</b> yêu cầu chứ không trong đường dẫn, vì cùng lý do đã nêu ở
 * {@link RedeemInvitationRequest}: nó là bí mật.</p>
 */
@Schema(description = "Nhận lời mời; kèm địa chỉ thư khi người nhận chưa có tài khoản")
public record AcceptInvitationRequest(

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Mã mời; dấu gạch, khoảng trắng và chữ thường đều chấp nhận được",
                example = "K7M2Q-D9HFX", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Email
        @Size(max = 254)
        @Schema(description = "Địa chỉ thư sẽ thành tên đăng nhập. Bắt buộc khi chưa có tài khoản;"
                + " bị bỏ qua khi yêu cầu mang token.", example = "ba.lan@example.com")
        String email,

        @Size(max = 120)
        @Schema(description = "Tên hiển thị cho tài khoản mới", example = "Nguyễn Thị Lan")
        String displayName) {
}
