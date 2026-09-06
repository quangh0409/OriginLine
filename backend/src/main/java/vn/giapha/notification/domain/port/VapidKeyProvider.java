package vn.giapha.notification.domain.port;

import java.util.Optional;

/**
 * Cổng đọc <b>khoá công khai</b> VAPID để phục vụ {@code GET /api/v1/push/public-key}.
 *
 * <p>Tồn tại để tầng {@code api} không phải với tay vào {@code infrastructure.webpush} — chiều phụ
 * thuộc là {@code api → application → domain}, một chiều duy nhất. Không có cổng này thì controller
 * sẽ import thẳng adapter, và đó là vết nứt đầu tiên trong kiến trúc hexagonal: nó luôn bắt đầu
 * bằng "chỉ một trường hợp thôi mà".</p>
 *
 * <p><b>Chỉ khoá công khai.</b> Khoá riêng không có cổng nào, không có getter nào, và không rời khỏi
 * {@code WebPushProperties} — nó chỉ được dùng tại chỗ để ký JWT.</p>
 */
public interface VapidKeyProvider {

    /** @return rỗng khi chưa cấu hình VAPID, nghĩa là kênh Web Push đang tắt */
    Optional<String> publicKeyBase64Url();
}
