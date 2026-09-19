package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân yêu cầu cho ba thao tác dùng mã: xem · nhận · từ chối.
 *
 * <h2>Vì sao mã đi trong THÂN yêu cầu chứ không trong đường dẫn</h2>
 * Mã mời là bí mật duy nhất của cả luồng. Một mã nằm trong đường dẫn sẽ đi vào access log của
 * reverse proxy, vào lịch sử trình duyệt, vào header {@code Referer} của mọi tài nguyên trang đó
 * tải về, và vào mọi hệ thống giám sát đang gom URL. Thân của một yêu cầu {@code POST} thì không đi
 * vào chỗ nào trong số đó.
 *
 * <p>Đây cũng là lý do cả ba thao tác — kể cả thao tác chỉ <i>đọc</i> — đều là {@code POST}. Tác
 * dụng phụ có ích: {@code POST} không được cache, mà một màn hình hiện tên người còn sống thì không
 * nên nằm trong cache nào.</p>
 *
 * <p>Đường dẫn mà người dùng thấy ({@code /moi/K7M2Q-D9HFX}) là route của frontend, không phải của
 * API; frontend đọc mã từ route của mình rồi gửi xuống trong thân yêu cầu.</p>
 */
@Schema(description = "Mã mời do Trưởng chi phát")
public record RedeemInvitationRequest(

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Mã mời; dấu gạch, khoảng trắng và chữ thường đều chấp nhận được",
                example = "K7M2Q-D9HFX", requiredMode = Schema.RequiredMode.REQUIRED)
        String code) {
}
