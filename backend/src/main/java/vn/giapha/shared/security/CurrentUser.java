package vn.giapha.shared.security;

import java.util.Set;

/**
 * Người dùng đang thao tác, dựng từ JWT do Keycloak cấp.
 *
 * <p>{@code keycloakSub} là khoá nối sang bảng {@code app_user}; từ đó mới ra được
 * {@code person_id} (chuỗi ánh xạ {@code keycloak_sub → app_user → person} của TDD). Ở W0 chưa có
 * bảng nào nên {@code personId} chưa xuất hiện ở đây; context {@code membership} sẽ bổ sung dịch vụ
 * phân giải khi W6 làm tới.</p>
 *
 * @param keycloakSub claim {@code sub} - dinh danh on dinh cua tai khoan
 * @param username    claim {@code preferred_username}
 * @param email       claim {@code email}, có thể null
 * @param roles       vai trò đã chuẩn hoá (không mang tiền tố {@code ROLE_})
 */
public record CurrentUser(String keycloakSub, String username, String email, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
