package vn.giapha.notification.application.view;

/**
 * Kết quả một lượt gửi thử Web Push tới thiết bị của chính người gọi.
 *
 * @param sent        có ít nhất một thiết bị nhận được hay không
 * @param outcome     {@code SENT} / {@code RETRYABLE} / {@code PERMANENT} / {@code SKIPPED}.
 *                    {@code PERMANENT} gần như luôn là cấu hình VAPID sai — push service đã
 *                    <b>từ chối</b> máy chủ này, và thử lại không sửa được gì.
 * @param detail      mô tả từ adapter, gồm cả mã HTTP mà push service trả về
 * @param deviceCount số thiết bị đang hoạt động của người gọi. {@code 0} là lý do phổ biến nhất
 *                    khiến "gửi thử" không thấy gì mà cũng không có lỗi nào
 */
public record WebPushTestSendView(boolean sent, String outcome, String detail, int deviceCount) {
}
