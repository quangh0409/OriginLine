package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mã mời dòng họ.
 *
 * <p>Đi trong <b>thân</b> của một {@code POST}, kể cả ở đường chỉ đọc: mã là bí mật, và đặt nó vào
 * đường dẫn là đặt nó vào access log của reverse proxy, lịch sử trình duyệt, header
 * {@code Referer} và mọi hệ thống giám sát đang gom URL.</p>
 */
@Schema(description = "Mã mời dòng họ")
public record RedeemClanInviteRequest(

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Mã mời; dấu gạch, khoảng trắng và chữ thường đều chấp nhận được",
                example = "K7M2Q-D9HFX", requiredMode = Schema.RequiredMode.REQUIRED)
        String code) {
}
