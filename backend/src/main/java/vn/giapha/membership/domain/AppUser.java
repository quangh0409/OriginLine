package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tài khoản hệ thống — mắt xích giữa của chuỗi {@code keycloak_sub → app_user → person}.
 *
 * <h2>Vì sao không lưu mật khẩu, vai trò hay email làm nguồn chân lý</h2>
 * Xác thực thuộc về Keycloak. Bảng này chỉ giữ đúng thứ Keycloak không biết: nhân khẩu tương ứng
 * trong cây phả hệ và trạng thái nghiệp vụ. {@code email} và {@code displayName} là bản sao tiện
 * dụng để hiển thị danh sách mà không phải gọi sang Keycloak — <b>không</b> dùng để xác thực, và
 * {@code email} là dữ liệu Tầng 3 nên không được trả cho vai không đủ quyền.
 *
 * <h2>{@code personId} có thể null</h2>
 * Một người vừa đăng nhập bằng Google lần đầu chưa được ghép vào cây. Chưa ghép thì không có
 * "chi nhà", nên mọi phép xét "cùng chi" của Tầng 2 đều trượt — đúng hướng an toàn.
 *
 * <p>POJO thuần: không {@code @Entity}, không {@code @Component}. Bản chiếu JPA nằm ở
 * {@code membership.infrastructure.jpa}.</p>
 */
public final class AppUser {

    private final UUID id;
    private final String keycloakSub;
    private UUID personId;
    private String email;
    private String displayName;
    private AppUserStatus status;
    private String locale;
    private Instant lastLoginAt;
    private final long version;

    public AppUser(UUID id, String keycloakSub, UUID personId, String email, String displayName,
                   AppUserStatus status, String locale, Instant lastLoginAt, long version) {
        this.id = Objects.requireNonNull(id, "AppUser.id khong duoc null");
        this.keycloakSub = requireSub(keycloakSub);
        this.personId = personId;
        this.email = email;
        this.displayName = displayName;
        this.status = status == null ? AppUserStatus.PENDING : status;
        this.locale = normalizeLocale(locale);
        this.lastLoginAt = lastLoginAt;
        this.version = version;
    }

    /** Tài khoản mới toanh, sinh ra ở lần đăng nhập đầu tiên. */
    public static AppUser register(UUID id, String keycloakSub, String email, String displayName) {
        return new AppUser(id, keycloakSub, null, email, displayName,
                AppUserStatus.PENDING, "vi", null, 0L);
    }

    public UUID id() {
        return id;
    }

    public String keycloakSub() {
        return keycloakSub;
    }

    public UUID personId() {
        return personId;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public AppUserStatus status() {
        return status;
    }

    public String locale() {
        return locale;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public long version() {
        return version;
    }

    public boolean isLinkedToTree() {
        return personId != null;
    }

    /**
     * Ghép tài khoản với một nhân khẩu.
     *
     * <p>Chỉ ghép được <b>một lần</b>. {@code ux_app_user_person} đã chặn hai tài khoản cùng trỏ
     * một nhân khẩu ở phía CSDL; chặn thêm ở đây để lỗi lộ ra bằng thông điệp nghiệp vụ chứ không
     * bằng một {@code DataIntegrityViolationException}. Ghép lại sang người khác là nghiệp vụ
     * khác hẳn — nó phải để lại vết {@code UPDATE} có lý do, không được lẫn vào lối "ghép lần đầu".</p>
     */
    public void linkPerson(UUID target) {
        Objects.requireNonNull(target, "personId khong duoc null khi ghep tai khoan vao cay");
        if (personId != null && !personId.equals(target)) {
            throw new IllegalStateException(
                    "Tai khoan da duoc ghep voi nhan khau " + personId + ", khong tu doi sang " + target);
        }
        this.personId = target;
        if (this.status == AppUserStatus.PENDING) {
            this.status = AppUserStatus.ACTIVE;
        }
    }

    /** Cập nhật thông tin hiển thị lấy từ token ở mỗi lần đăng nhập. */
    public void refreshProfile(String newEmail, String newDisplayName, Instant loginAt) {
        if (newEmail != null && !newEmail.isBlank()) {
            this.email = newEmail.trim();
        }
        if (newDisplayName != null && !newDisplayName.isBlank()) {
            this.displayName = newDisplayName.trim();
        }
        if (loginAt != null) {
            this.lastLoginAt = loginAt;
        }
    }

    public void changeStatus(AppUserStatus next) {
        this.status = Objects.requireNonNull(next, "status khong duoc null");
    }

    public void changeLocale(String next) {
        this.locale = normalizeLocale(next);
    }

    private static String requireSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            throw new IllegalArgumentException("keycloak_sub khong duoc rong");
        }
        return keycloakSub.trim();
    }

    /** {@code ck_app_user_locale} chỉ nhận {@code vi} hoặc {@code en}. */
    private static String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "vi";
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return "en".equals(value) ? "en" : "vi";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
