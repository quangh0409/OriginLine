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
     * Số lần gửi lỗi <b>liên tiếp</b> trước khi coi đăng ký là hỏng và tắt nó đi.
     *
     * <p>Không có ngưỡng thì {@code failure_count} chỉ là một con số tăng mãi mà không ai đọc: một
     * endpoint chết vĩnh viễn nhưng trả 5xx (thay vì 410) sẽ được gọi lại vào mỗi mùa giỗ, mãi mãi,
     * và mỗi lượt gọi ấy làm chậm đúng cái hàng đợi đang phải gửi nhắc giỗ cho cả họ.</p>
     *
     * <p>Chọn 5 chứ không phải 1: 5xx và lỗi mạng phần lớn là sự cố tạm thời của push service, tắt
     * ngay lần đầu sẽ khiến bà con mất thông báo vì một phút chập chờn của Google. Đếm được đặt lại
     * về 0 ở mọi lần gửi thành công và mọi lần đăng ký lại, nên 5 lần này là 5 lần <b>liên tiếp</b>.</p>
     */
    int NGUONG_LOI_LIEN_TIEP = 5;

    /**
     * Ghi mới hoặc cập nhật theo {@code endpoint} (khoá định danh chuẩn Web Push).
     *
     * @return bản ghi sau khi ghi, kèm cờ cho biết là tạo mới hay cập nhật
     */
    Upsert save(UUID appUserId, String endpoint, String p256dh, String auth, String userAgent,
                Instant expiresAt);

    /**
     * Các đăng ký <b>còn gửi được</b> của một tài khoản.
     *
     * <p>Loại bỏ cả đăng ký đã tắt (quá {@link #NGUONG_LOI_LIEN_TIEP} lần lỗi liên tiếp) lẫn đăng ký
     * đã <b>quá hạn</b> theo {@code expires_at}. Trình duyệt tự cung cấp hạn ấy khi đăng ký; bỏ qua
     * nó nghĩa là vẫn đều đặn gửi vào một endpoint đã hết hạn cho tới khi push service chịu trả 410 —
     * mà có push service không bao giờ trả 410 cho endpoint hết hạn.</p>
     */
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

    /**
     * Đếm lỗi tạm thời liên tiếp; đủ {@link #NGUONG_LOI_LIEN_TIEP} lần thì <b>tắt</b> đăng ký
     * ({@code is_active = FALSE}).
     *
     * <p>Tắt chứ không xoá: xoá là dành cho 404/410, tức push service đã khẳng định đăng ký không còn
     * tồn tại. Ở đây ta chỉ <i>suy đoán</i> từ một chuỗi lỗi tạm thời, nên giữ lại bản ghi để màn
     * hình quản lý thiết bị còn giải thích được với người dùng vì sao máy của họ ngừng nhận thông
     * báo. Đăng ký lại cùng endpoint sẽ bật lại và đặt bộ đếm về 0.</p>
     *
     * @return {@code true} nếu chính lần gọi này làm đăng ký bị tắt
     */
    boolean recordFailure(UUID id);

    record Upsert(PushSubscription subscription, boolean created) {
    }
}
