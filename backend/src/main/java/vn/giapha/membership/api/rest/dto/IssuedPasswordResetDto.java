package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.giapha.membership.application.IssuedPasswordReset;

/**
 * Phản hồi của lệnh cấp lại — <b>lần duy nhất liên kết rời máy chủ</b>.
 *
 * <h2>Đọc kỹ trước khi dựng giao diện quanh kiểu này</h2>
 * {@link #url()} không đọc lại được ở bất cứ endpoint nào; trong CSDL không có token, kể cả băm.
 * Màn hình vì vậy phải cho người cấp <b>chép ngay</b> (hoặc mở sẵn nút chia sẻ sang Zalo) và phải
 * nói rõ rằng đóng màn là mất liên kết. Cấp lại thì tự thu hồi liên kết cũ.
 *
 * <h2>Ba câu mà màn hình BẮT BUỘC phải nói, không phải ba câu trang trí</h2>
 * <ol>
 *   <li><b>"Chỉ đưa cho đúng người."</b> Ai cầm liên kết này trong nửa giờ tới là đặt được mật
 *       khẩu của người kia.</li>
 *   <li><b>"Có hiệu lực tới {@code expiresAt}."</b> Hết hạn là hết, không gia hạn được.</li>
 *   <li><b>"Chính chủ đã được thông báo."</b> Câu này để người cấp <i>biết</i> rằng việc mình vừa
 *       làm không ẩn danh — và đó là một phần của phép răn, không chỉ là một thông tin.</li>
 * </ol>
 */
@Schema(description = "Liên kết đặt mật khẩu vừa cấp. Chỉ xuất hiện đúng một lần, không tra lại được.")
public record IssuedPasswordResetDto(

        @Schema(description = "Địa chỉ trao tận tay người quên mật khẩu (hoặc nhắn qua Zalo)."
                + " Dùng một lần, hết hạn sau 30 phút.",
                example = "https://giapha.vn/vi/dat-mat-khau?token=...")
        String url,

        @Schema(description = "Liên kết hết hiệu lực lúc này")
        Instant expiresAt,

        PasswordResetGrantDto grant) {

    public static IssuedPasswordResetDto from(IssuedPasswordReset issued) {
        return new IssuedPasswordResetDto(issued.url(), issued.expiresAt(),
                PasswordResetGrantDto.from(issued.grant()));
    }
}
