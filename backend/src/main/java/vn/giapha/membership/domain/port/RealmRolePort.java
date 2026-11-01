package vn.giapha.membership.domain.port;

import vn.giapha.membership.domain.RoleCode;

/**
 * Cổng gắn <b>vai trò cấp realm</b> cho một tài khoản ở nhà cung cấp danh tính (Keycloak).
 *
 * <h2>Vì sao là cổng riêng, không thêm vào {@link IdentityProviderPort}</h2>
 * {@code IdentityProviderPort} ghi rõ "không gán vai trò" — và đó là một lời hứa đáng giữ: nó được
 * tiêm vào mọi lối mời, kể cả lối <b>không cần đăng nhập</b> ({@code /clan-invites/register}).
 * Nếu năng lực gán vai nằm trên cùng cổng ấy thì một lỗi ở lối mời là một lối tự phong Hội đồng.
 * Tách cổng khiến năng lực này chỉ tiêm được vào đúng một lớp đã cân nhắc —
 * {@code CouncilAccountService}, vốn đòi vai {@code ADMIN}. Cùng lý do đã tách
 * {@link PasswordResetPort}.
 *
 * <h2>Vai ở realm chỉ là một nửa</h2>
 * {@code MemberScopeService} lấy <i>vai</i> từ token, còn <i>phạm vi</i> từ
 * {@code branch_assignment}. Tài khoản Hội đồng cần cả hai: vai {@code COUNCIL} trong realm (để
 * token mang nó) và một dòng phân công {@code COUNCIL} không gắn chi (để phạm vi là cả họ).
 * Thiếu vế thứ nhất thì {@code hasRole('COUNCIL')} trượt dù bảng phân công đúng.
 */
public interface RealmRolePort {

    /** Đã có đủ cấu hình để gọi Keycloak chưa (thiếu bí mật thì cổng tự tắt). */
    boolean isConfigured();

    /**
     * Gắn vai realm {@code role} cho tài khoản {@code subject}. <b>Lặp lại được</b>: tài khoản đã
     * mang vai ấy thì không làm gì.
     *
     * @throws IdentityProviderException khi Keycloak từ chối, không với tới được, hoặc realm không
     *         có vai ấy
     */
    void grantRealmRole(String subject, RoleCode role);
}
