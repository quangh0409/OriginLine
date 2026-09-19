package vn.giapha.notification.domain;

/**
 * Trạng thái cấu hình kênh Web Push, dành cho <b>người vận hành</b>.
 *
 * <h2>Vì sao cần một kiểu riêng thay vì chỉ một cờ boolean</h2>
 * Khi thông báo đẩy "không chạy", người quản trị cần phân biệt được ba tình huống khác hẳn nhau mà
 * một cờ {@code configured} gộp chung: kênh <i>bị tắt bằng tay</i>, <i>thiếu khoá</i>, hay
 * <i>thiếu đúng một nửa cặp khoá</i> (đặt được {@code PUBLIC_KEY} mà quên {@code PRIVATE_KEY} là ca
 * hay gặp nhất, vì khoá công khai được dán qua lại nhiều nơi còn khoá riêng thì không).
 *
 * <p><b>Không có trường nào mang khoá riêng.</b> Chỉ có cờ "đã đặt hay chưa". Khoá riêng không rời
 * {@code WebPushProperties}, không vào cơ sở dữ liệu, không vào log, không vào phản hồi API.</p>
 *
 * @param enabled          công tắc {@code giapha.webpush.enabled} — tắt tay mà không phải xoá khoá
 * @param publicKeySet     đã đặt {@code GIAPHA_WEBPUSH_PUBLIC_KEY} hay chưa
 * @param privateKeySet    đã đặt {@code GIAPHA_WEBPUSH_PRIVATE_KEY} hay chưa (chỉ cờ, không giá trị)
 * @param publicKey        khoá công khai đang dùng, {@code null} khi chưa sẵn sàng
 * @param subject          claim {@code sub} của JWT VAPID — để trống là lý do phổ biến khiến 400
 * @param ttlSeconds       thời gian push service giữ tin khi thiết bị offline
 */
public record WebPushConfigStatus(boolean enabled,
                                  boolean publicKeySet,
                                  boolean privateKeySet,
                                  String publicKey,
                                  String subject,
                                  int ttlSeconds) {

    /**
     * Sẵn sàng gửi hay chưa.
     *
     * <p>Suy ra từ {@link #publicKey()} — trường này chỉ có giá trị khi cặp khoá đã <b>giải mã
     * thành công và đã qua phép tự kiểm cùng cặp</b> lúc khởi động, nên nó là bằng chứng mạnh hơn
     * "hai biến môi trường đều khác rỗng".</p>
     */
    public boolean ready() {
        return publicKey != null && !publicKey.isBlank();
    }
}
