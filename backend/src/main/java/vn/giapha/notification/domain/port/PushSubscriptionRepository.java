package vn.giapha.notification.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.PushSubscription;

/**
 * Cổng quản lý đăng ký Web Push.
 *
 * <p><b>Đây là xoá cứng.</b> Quy tắc "chỉ xoá mềm" của hệ thống áp cho <i>nhân khẩu trong gia phả</i>
 * — xoá cứng một người làm đứt liên kết cây. Một đăng ký thiết bị không mang ý nghĩa phả hệ nào;
 * giữ lại bản ghi chết chỉ khiến mỗi lần gửi tốn thêm một lượt gọi HTTP để nhận về 410.</p>
 */
public interface PushSubscriptionRepository {

    /**
     * Ghi mới hoặc cập nhật theo {@code endpoint} (khoá định danh chuẩn Web Push).
     *
     * @return bản ghi sau khi ghi, kèm cờ cho biết là tạo mới hay cập nhật
     */
    Upsert save(UUID appUserId, String endpoint, String p256dh, String auth, String userAgent,
                Instant expiresAt);

    List<PushSubscription> activeByAppUser(UUID appUserId);

    Optional<PushSubscription> findOwned(UUID id, UUID appUserId);

    /** Người dùng tự tắt trong phần cài đặt. */
    boolean deleteOwned(UUID id, UUID appUserId);

    /**
     * Push service trả 404/410 ⇒ đăng ký đã bị thu hồi hoặc hết hạn. Xoá ngay, <b>không retry</b>:
     * mọi lần gửi sau đều sẽ nhận đúng mã ấy.
     */
    boolean deleteByEndpoint(String endpoint);

    /** Ghi nhận lần gửi thành công gần nhất — hiển thị ở màn hình quản lý thiết bị. */
    void touchLastUsed(UUID id, Instant when);

    /** Đếm lỗi tạm thời liên tiếp; đủ nhiều thì đăng ký coi như hỏng. */
    void recordFailure(UUID id);

    record Upsert(PushSubscription subscription, boolean created) {
    }
}
