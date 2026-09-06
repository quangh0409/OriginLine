package vn.giapha.notification.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Người nhận một thông báo.
 *
 * <p>Vừa có {@code personId} (khoá của {@code notification_inbox} và {@code notification_log}) vừa
 * có {@code appUserId} (khoá của {@code push_subscription}). Giữ cả hai trong một VO để consumer
 * khỏi tra ngược lại cơ sở dữ liệu ở giữa luồng gửi.</p>
 *
 * @param locale {@code vi} hoặc {@code en} — lấy từ {@code app_user.locale}
 */
public record Recipient(UUID personId, UUID appUserId, String locale) {

    public Recipient {
        Objects.requireNonNull(personId, "Recipient.personId khong duoc null");
        locale = normalize(locale);
    }

    public boolean prefersEnglish() {
        return "en".equals(locale);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "vi";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return "en".equals(value) ? "en" : "vi";
    }
}
