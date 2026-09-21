package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nhận lời mời cá nhân; kèm định danh đăng nhập khi người nhận chưa có tài khoản.
 *
 * <h2>Nhận CẢ số điện thoại, và ở luồng này điều đó quan trọng nhất</h2>
 * Mã mời cá nhân dành cho <b>các cụ lớn tuổi</b> — Trưởng chi chọn người trong phả, in phiếu, đưa
 * tận tay. Đó đúng là nhóm thường <i>không có</i> email. Nếu chỉ ô đăng ký bằng mã dòng họ nhận số
 * điện thoại còn lối này thì không, thì nhóm người mà lối này sinh ra để phục vụ lại là nhóm duy
 * nhất không dùng được nó.
 */
@Schema(description = "Nhận lời mời; kèm email hoặc số điện thoại khi người nhận chưa có tài khoản")
public record AcceptInvitationRequest(

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Mã mời; dấu gạch, khoảng trắng và chữ thường đều chấp nhận được",
                example = "K7M2Q-D9HFX", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Size(max = 254)
        @Schema(description = "Định danh đăng nhập: **địa chỉ thư điện tử HOẶC số điện thoại**."
                + " Số được chuẩn hoá về dạng `0…` (nhận `+84…`, `84…`, dấu cách, dấu chấm, dấu"
                + " gạch). Bắt buộc khi chưa có tài khoản; bị bỏ qua khi yêu cầu mang token.",
                example = "ba.lan@example.com")
        String loginId,

        @Size(max = 254)
        @Schema(deprecated = true,
                description = "**Tên cũ của `loginId`.** Giữ lại để bản giao diện đang chạy không"
                        + " gãy; sẽ bỏ.")
        String email,

        @Size(max = 120)
        @Schema(description = "Tên hiển thị cho tài khoản mới", example = "Nguyễn Thị Lan")
        String displayName) {

    /** Định danh đăng nhập, ưu tiên {@link #loginId()} rồi mới tới tên cũ {@link #email()}. */
    public String dinhDanhDangNhap() {
        return loginId != null && !loginId.isBlank() ? loginId : email;
    }
}
