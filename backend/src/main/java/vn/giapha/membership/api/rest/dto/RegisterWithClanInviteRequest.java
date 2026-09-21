package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Đăng ký tài khoản bằng mã mời dòng họ. Mã được kiểm <b>trước khi</b> tài khoản được tạo. */
@Schema(description = "Đăng ký tài khoản bằng mã mời dòng họ")
public record RegisterWithClanInviteRequest(

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Mã mời dòng họ", example = "K7M2Q-D9HFX",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Size(max = 254)
        @Schema(description = "Định danh đăng nhập: **địa chỉ thư điện tử HOẶC số điện thoại**."
                + " Ô đăng nhập của realm nhận cả hai — quyết định ấy có để phục vụ các cụ không có"
                + " email — nên ô đăng ký cũng nhận cả hai. Số điện thoại được chuẩn hoá về dạng"
                + " `0…` (nhận `+84…`, `84…`, dấu cách, dấu chấm, dấu gạch)."
                + " **Bắt buộc khi gọi không kèm token**; bị bỏ qua khi yêu cầu mang token.",
                example = "ba.lan@example.com")
        String loginId,

        @Size(max = 254)
        @Schema(deprecated = true,
                description = "**Tên cũ của `loginId`.** Giữ lại để bản giao diện đang chạy không"
                        + " gãy; sẽ bỏ. Dùng `loginId` — tên ấy không nói dối khi giá trị là một số"
                        + " điện thoại.")
        String email,

        @Size(max = 160)
        @Schema(description = "Tên hiển thị tự khai. KHÔNG phải tên trong phả, và KHÔNG được dùng"
                + " để tự động suy ra nhân khẩu nào: việc ghép là của đơn tự nhận và của Trưởng chi.",
                example = "Nguyễn Thị Lan")
        String displayName) {

    /**
     * Định danh đăng nhập, ưu tiên {@link #loginId()} rồi mới tới tên cũ {@link #email()}.
     *
     * <p>Hai tên cho một giá trị là nợ, và nó được trả bằng cách xoá {@code email} — <b>không</b>
     * bằng cách giữ một trường tên {@code email} chở một số điện thoại, vì đó là một cái tên nói
     * dối và nó sẽ sống lâu hơn mọi ghi chú giải thích.</p>
     */
    public String dinhDanhDangNhap() {
        return loginId != null && !loginId.isBlank() ? loginId : email;
    }
}
