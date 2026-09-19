package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân yêu cầu của {@code POST /api/v1/invitations/set-password}.
 *
 * <p>Cả hai trường đều là bí mật, nên cả hai đều đi trong <b>thân</b> của một {@code POST} — không
 * bao giờ trong đường dẫn hay tham số truy vấn, nơi chúng sẽ vào access log của reverse proxy và
 * lịch sử trình duyệt. Riêng {@link #token()} thì trước đó <i>đã</i> nằm trong thanh địa chỉ một
 * lần (nó là một phần của liên kết người dùng bấm) — đó chính là lý do nó có hạn nửa giờ và chết
 * ngay khi mật khẩu được đặt.
 *
 * <p><b>Không có luật độ mạnh mật khẩu ở đây</b>, chỉ có chặn trên để không gửi đi một yêu cầu
 * chắc chắn hỏng. Luật thật là chính sách mật khẩu của realm Keycloak; chép lại nó xuống backend là
 * tạo ra một nguồn chân lý thứ hai sẽ lệch ngay lần đầu ai đó siết chính sách.</p>
 */
@Schema(description = "Người được mời tự đặt mật khẩu qua liên kết một lần")
public record SetPasswordRequest(

        @NotBlank
        @Size(max = 512)
        @Schema(description = "Token lấy từ setPasswordUrl",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,

        @NotBlank
        @Size(max = 256)
        @Schema(description = "Mật khẩu do chính người dùng chọn; realm Keycloak kiểm độ mạnh",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {
}
