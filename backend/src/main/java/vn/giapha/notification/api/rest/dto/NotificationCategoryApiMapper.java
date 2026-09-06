package vn.giapha.notification.api.rest.dto;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.giapha.notification.domain.NotificationCategory;

/**
 * Quy đổi loại thông báo giữa <b>mã cơ sở dữ liệu</b> và <b>mã hợp đồng</b>.
 *
 * <table border="1">
 *   <caption>Ánh xạ</caption>
 *   <tr><th>{@code ck_notification_inbox_category} (V4)</th><th>{@code NotificationCategory} (OpenAPI)</th></tr>
 *   <tr><td>{@code REMINDER}</td><td>{@code GIO_REMINDER}</td></tr>
 *   <tr><td>{@code APPROVAL}</td><td>{@code CHANGE_REQUEST}</td></tr>
 *   <tr><td>{@code SYSTEM}</td><td>{@code SYSTEM}</td></tr>
 *   <tr><td>{@code NEWS}</td><td>{@code EVENT}</td></tr>
 * </table>
 *
 * <p><b>Chỗ khiên cưỡng, cần chốt lại:</b> {@code NEWS} (bản tin dòng họ) ánh xạ sang {@code EVENT}
 * ("thông báo sự kiện dòng họ khác") vì đó là mã gần nhất trong hợp đồng — nhưng hai khái niệm không
 * trùng nhau. Bản tin và thông báo sự kiện là hai thứ khác nhau với người đọc. Cần một trong hai:
 * thêm {@code NEWS} vào hợp đồng, hoặc bỏ {@code NEWS} khỏi ràng buộc CHECK. Cả hai đều nằm ngoài
 * quyền sửa của W5 (hợp đồng và migration đều có chủ sở hữu khác).</p>
 */
public final class NotificationCategoryApiMapper {

    private static final Logger log = LoggerFactory.getLogger(NotificationCategoryApiMapper.class);

    private NotificationCategoryApiMapper() {
    }

    public static String toApi(NotificationCategory category) {
        return switch (category) {
            case REMINDER -> "GIO_REMINDER";
            case APPROVAL -> "CHANGE_REQUEST";
            case SYSTEM -> "SYSTEM";
            case NEWS -> "EVENT";
        };
    }

    /**
     * @return {@code null} khi không có bộ lọc hoặc mã không nhận dạng được — mã lạ bị bỏ qua kèm
     *         log thay vì ném lỗi, để client cũ không làm hỏng cả trang hộp thư
     */
    public static NotificationCategory toDomain(String apiCategory) {
        if (apiCategory == null || apiCategory.isBlank()) {
            return null;
        }
        return switch (apiCategory.trim().toUpperCase(Locale.ROOT)) {
            case "GIO_REMINDER" -> NotificationCategory.REMINDER;
            case "CHANGE_REQUEST" -> NotificationCategory.APPROVAL;
            case "SYSTEM" -> NotificationCategory.SYSTEM;
            case "EVENT" -> NotificationCategory.NEWS;
            default -> {
                log.info("Bo qua bo loc category khong nhan dang duoc: {}", apiCategory);
                yield null;
            }
        };
    }
}
