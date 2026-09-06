package vn.giapha.membership.domain;

import java.util.Collection;
import java.util.Locale;

/**
 * Vai trò <b>kỹ thuật</b>, khớp {@code ck_role_code} của {@code V5__membership_audit.sql}.
 *
 * <h2>Tách bạch với chức danh dòng tộc</h2>
 * {@code Tộc trưởng}, {@code Trưởng chi} là dữ kiện huyết thống/đích tôn, nằm ở
 * {@code branch.head_person_id}. Vai ở đây chỉ nói người đó được <i>thao tác</i> gì trên hệ thống.
 * Một người có thể giữ cả hai, chỉ một, hoặc không cái nào — ba dữ kiện độc lập, không suy ra được
 * cái này từ cái kia. Trưởng chi theo dòng tộc chưa chắc có tài khoản; tài khoản mang vai
 * {@link #BRANCH_HEAD} chưa chắc là Trưởng chi trong họ.
 *
 * <h2>Vai chỉ là một nửa</h2>
 * Nửa còn lại là phạm vi chi/ngành theo {@code ltree}, lưu ở {@code branch_assignment} và
 * <b>không thể suy ra từ token</b>. Có vai {@link #BRANCH_HEAD} không đồng nghĩa được đụng mọi
 * người trong họ — xem {@code MemberScope}.
 */
public enum RoleCode {

    /** Quản trị hệ thống — kỹ thuật, toàn cục. Không phải chức danh dòng tộc. */
    ADMIN(100),

    /** Hội đồng Tộc biểu / Tộc trưởng — phạm vi toàn dòng họ. */
    COUNCIL(80),

    /** Trưởng Chi/Ngành — <b>chỉ</b> trong các chi được giao. */
    BRANCH_HEAD(60),

    /** Thành viên đã đăng nhập: xem theo phạm vi, sửa hồ sơ mình, gửi yêu cầu đính chính. */
    MEMBER(40),

    /** Khách vãng lai. <b>Không được thấy bất kỳ người còn sống nào.</b> */
    GUEST(10);

    private final int rank;

    RoleCode(int rank) {
        this.rank = rank;
    }

    /** Thứ bậc để so sánh nhanh; <b>không</b> thay thế cho kiểm tra phạm vi chi/ngành. */
    public int rank() {
        return rank;
    }

    /** {@code true} với hai vai có phạm vi toàn dòng họ. */
    public boolean isClanWide() {
        return this == ADMIN || this == COUNCIL;
    }

    /** {@code true} nếu vai này có thể duyệt yêu cầu đính chính (còn phải qua kiểm phạm vi chi). */
    public boolean canReview() {
        return this == ADMIN || this == COUNCIL || this == BRANCH_HEAD;
    }

    /**
     * Vai <b>rộng nhất</b> trong một tập vai trò đã chuẩn hoá (không tiền tố {@code ROLE_}).
     *
     * <p>Tập rỗng trả {@link #GUEST}, không phải {@code MEMBER}: mặc định phải là vai hẹp nhất.
     * Nơi gọi biết chắc người dùng đã đăng nhập thì tự nâng lên.</p>
     */
    public static RoleCode broadest(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return GUEST;
        }
        RoleCode broadest = GUEST;
        for (String raw : roles) {
            RoleCode candidate = parse(raw);
            if (candidate != null && candidate.rank > broadest.rank) {
                broadest = candidate;
            }
        }
        return broadest;
    }

    /** {@code null} khi chuỗi không phải một vai đã biết — vai lạ trong token bị bỏ qua, không ném. */
    public static RoleCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (RoleCode code : values()) {
            if (code.name().equals(normalized)) {
                return code;
            }
        }
        return null;
    }
}
