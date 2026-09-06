package vn.giapha.notification.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.Recipient;

/**
 * Cổng phân giải <b>ai được nhắc</b>.
 *
 * <p>Quy tắc (BA v2 / FR-2.2): người nhận là thành viên thuộc <b>cùng chi/ngành của sự kiện và mọi
 * nhánh con</b>, so bằng {@code ltree} (toán tử hậu duệ-hoặc-chính-nó trên {@code branch.path}).
 * Sự kiện cấp dòng họ thì cả họ nhận.</p>
 *
 * <h2>Nợ kiến trúc đã biết</h2>
 * {@code app_user} thuộc context {@code membership} (W6); {@code person} và {@code branch} thuộc
 * {@code genealogy} (W2). Cả hai chưa mở mặt tiền công khai, nên hiện thực Giai đoạn 1 đọc thẳng
 * các bảng ấy bằng SQL — cùng loại nợ và cùng cách trả như {@code CallerIdentityJdbcAdapter}: ở cấp
 * Java không có phụ thuộc nào sang context khác, và khi có application service thì adapter chuyển
 * sang gọi service rồi bỏ SQL.
 */
public interface RecipientDirectory {

    /**
     * @param branchId  chi/ngành đích; bỏ qua khi {@code clanLevel}
     * @param clanLevel {@code true} = cả dòng họ
     */
    List<Recipient> membersOfBranch(UUID branchId, boolean clanLevel);

    /** Tra một người nhận cụ thể — dùng cho thông báo gửi đích danh. */
    Optional<Recipient> byPersonId(UUID personId);

    /** Tra người nhận từ tài khoản đang đăng nhập (chuỗi keycloak_sub -&gt; app_user -&gt; person). */
    Optional<Recipient> byKeycloakSub(String keycloakSub);
}
