package vn.giapha.notification.domain;

import java.util.Locale;

/**
 * Kênh gửi thông báo — khớp {@code ck_notification_log_channel} của {@code V4__events.sql}.
 *
 * <p>Giai đoạn 1 chỉ hiện thực {@link #INAPP} và {@link #WEBPUSH}. {@link #ZALO}, {@link #SMS},
 * {@link #EMAIL} đã có mặt trong enum và trong ràng buộc CHECK ngay từ đầu để Giai đoạn 2 chỉ phải
 * <b>thêm một adapter</b> ({@code ZaloZnsAdapter}) chứ không phải sửa luồng, sửa schema hay sửa
 * consumer.</p>
 */
public enum Channel {

    /**
     * Hộp thư trong ứng dụng — <b>nguồn chân lý</b> của thông báo MVP. Người từ chối quyền Web Push
     * vẫn nhận đủ ở đây.
     */
    INAPP("notify.inapp"),

    /** Web Push (VAPID / RFC 8291) — lớp đẩy thêm ra ngoài, có thể không tới nơi. */
    WEBPUSH("notify.webpush"),

    /** Zalo ZNS — Giai đoạn 2. */
    ZALO("notify.zalo"),

    /** SMS — Giai đoạn 2. */
    SMS("notify.sms"),

    /** Email — Giai đoạn 3. */
    EMAIL("notify.email");

    private final String routingKey;

    Channel(String routingKey) {
        this.routingKey = routingKey;
    }

    /** Routing key trên exchange {@code notify}. */
    public String routingKey() {
        return routingKey;
    }

    public static Channel fromDbValue(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
