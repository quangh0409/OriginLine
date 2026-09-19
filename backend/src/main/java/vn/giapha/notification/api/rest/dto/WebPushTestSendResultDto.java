package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.notification.application.view.WebPushTestSendView;

/** Kết quả gửi thử Web Push — trả thẳng kết quả thật, kể cả khi thất bại. */
@Schema(name = "WebPushTestSendResult", description = "Ket qua gui thu Web Push cho chinh minh")
public record WebPushTestSendResultDto(
        @Schema(description = "Co it nhat mot thiet bi nhan duoc") boolean sent,
        @Schema(description = "SENT / RETRYABLE / PERMANENT / SKIPPED") String outcome,
        @Schema(description = "Mo ta tu adapter, gom ca ma HTTP cua push service") String detail,
        @Schema(description = "So thiet bi dang hoat dong cua nguoi goi") int deviceCount) {

    public static WebPushTestSendResultDto from(WebPushTestSendView view) {
        return new WebPushTestSendResultDto(view.sent(), view.outcome(), view.detail(),
                view.deviceCount());
    }
}
