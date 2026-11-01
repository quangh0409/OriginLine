package vn.giapha.membership.application;

import java.util.UUID;

/**
 * Quản trị hệ thống lập một tài khoản <b>Hội đồng Tộc biểu</b> — bước 2 của "Khởi tạo dòng họ".
 *
 * @param displayName tên hiển thị của người ấy (không phải tên trong phả)
 * @param loginId     số điện thoại Việt Nam <b>hoặc</b> email — cùng luật với
 *                    {@code POST /clan-invites/register} ({@code LoginIdentifier})
 * @param personId    nhân khẩu trong phả ứng với người ấy, nếu đã có; {@code null} = gắn sau
 * @param note        lý do, ví dụ số biên bản họp họ; đi vào {@code audit_log}
 *
 * <p>Nằm ở {@code membership.application} chứ không ở gói {@code command} như các lệnh khác: bên
 * gọi là context {@code clan}, và chỉ gói này mang {@code @NamedInterface}. Mở cả gói
 * {@code command} ra để lấy một kiểu là mở mọi lệnh ghi của context.</p>
 */
public record CreateCouncilAccountCommand(String displayName, String loginId, UUID personId,
                                          String note) {
}
