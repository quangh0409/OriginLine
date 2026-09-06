package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Trạng thái tài khoản, khớp {@code ck_app_user_status} của V5.
 *
 * <p>Chỉ {@link #ACTIVE} mới được thao tác. Ba trạng thái còn lại cố ý <b>không</b> xoá dòng
 * {@code app_user}: {@code audit_log.actor_user_id} trỏ tới nó, và một nhật ký mất người thực hiện
 * là nhật ký vô dụng.</p>
 */
public enum AppUserStatus {

    /** Vừa đăng nhập lần đầu, chưa được Hội đồng ghép vào cây phả hệ. */
    PENDING,

    ACTIVE,

    /** Tạm khoá — vi phạm nội quy, hoặc đang tranh chấp dữ liệu. */
    SUSPENDED,

    /** Ngừng hẳn: rời dòng họ, hoặc yêu cầu xoá dữ liệu theo Nghị định 13/2023. */
    DISABLED;

    public boolean canOperate() {
        return this == ACTIVE;
    }

    public static AppUserStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
