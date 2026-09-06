package vn.giapha.genealogy.application;

/**
 * Vai kỹ thuật của người gọi, đọc từ JWT do Keycloak cấp (claim {@code realm_access.roles}).
 *
 * <p><b>Tách bạch với chức danh dòng tộc.</b> {@code Tộc trưởng}, {@code Trưởng chi} là dữ liệu
 * nghiệp vụ theo huyết thống/đích tôn ({@code Branch.headPersonId}); vai ở đây chỉ nói người đó
 * được <i>thao tác</i> gì trên hệ thống. Một người có thể giữ cả hai, hoặc chỉ một.</p>
 *
 * <p><b>Vai chỉ là một nửa của phân quyền.</b> Nửa còn lại là phạm vi chi/ngành theo {@code ltree}
 * — xem {@link GenealogyAccessGuard}. Có vai {@link #BRANCH_HEAD} không đồng nghĩa được sửa mọi
 * người trong họ.</p>
 */
public enum CallerRole {

    /** Quản trị hệ thống — kỹ thuật, toàn cục. */
    ADMIN,

    /** Hội đồng Tộc biểu / Tộc trưởng — phạm vi toàn dòng họ. */
    COUNCIL,

    /** Trưởng Chi/Ngành — <b>chỉ</b> trong các chi được giao. */
    BRANCH_HEAD,

    /** Thành viên đã đăng nhập. */
    MEMBER,

    /** Khách vãng lai (không token). <b>Không được thấy bất kỳ người còn sống nào.</b> */
    GUEST;

    /** {@code true} với hai vai có phạm vi toàn dòng họ — được xem cả bản ghi đã xoá mềm. */
    public boolean isClanWide() {
        return this == ADMIN || this == COUNCIL;
    }

    /**
     * Phân giải từ tập role đã chuẩn hoá của JWT. Lấy vai <b>rộng nhất</b> mà token có: một tài
     * khoản vừa là {@code ADMIN} vừa là {@code MEMBER} thì thao tác với quyền {@code ADMIN}.
     */
    public static CallerRole from(java.util.Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return MEMBER;
        }
        for (CallerRole role : new CallerRole[] {ADMIN, COUNCIL, BRANCH_HEAD, MEMBER}) {
            if (roles.contains(role.name())) {
                return role;
            }
        }
        return MEMBER;
    }
}
