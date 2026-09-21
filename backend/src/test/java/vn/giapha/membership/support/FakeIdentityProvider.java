package vn.giapha.membership.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.NewIdentityAccount;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;

/**
 * Bản trong bộ nhớ của một realm Keycloak — <b>mô phỏng ràng buộc, không chỉ lưu map</b>.
 *
 * <p>Hai điều được giữ đúng như realm thật, vì không có chúng thì test sẽ xanh trên một trạng thái
 * Keycloak từ chối: email là <b>duy nhất</b> ({@code duplicateEmailsAllowed: false}), và liên kết
 * đặt mật khẩu chỉ dùng được khi tài khoản <b>chưa có mật khẩu</b>.</p>
 *
 * <p>{@link #failNextWith} dựng ca "Keycloak chết giữa chừng" — ca mà cả thiết kế thứ tự thao tác
 * sinh ra để chịu được.</p>
 */
public final class FakeIdentityProvider implements IdentityProviderPort {

    /** Mọi tài khoản trong realm giả, khoá là {@code subject}. */
    private final Map<String, IdentityAccount> accounts = new LinkedHashMap<>();

    /** Token còn hiệu lực -> subject. */
    private final Map<String, String> tokens = new LinkedHashMap<>();

    /** Số lần {@link #createAccount} thực sự tạo mới — soi ca "bấm hai lần". */
    private final List<String> created = new ArrayList<>();

    private boolean configured = true;

    /** Đồng hồ của realm giả — test điều khiển được để dựng ca "ngoài cửa sổ mồ côi". */
    private Clock clock = Clock.systemUTC();

    private RuntimeException failNext;

    // -------------------------------------------------------------------------------------
    // Dựng cảnh
    // -------------------------------------------------------------------------------------

    /**
     * Tìm theo <b>tên đăng nhập</b> — lối của tài khoản lập bằng số điện thoại.
     *
     * <p>Realm thật đặt {@code username} duy nhất bất kể cấu hình, và một tài khoản dùng số máy
     * <b>không có</b> thuộc tính {@code email} để mà tra. Bản giả phải mô phỏng đúng điều đó, nếu
     * không test sẽ xanh trên một trạng thái Keycloak từ chối.</p>
     */
    @Override
    public Optional<IdentityAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String wanted = username.trim().toLowerCase(java.util.Locale.ROOT);
        return accounts.values().stream()
                .filter(account -> wanted.equals(account.username()))
                .findFirst()
                .map(FakeIdentityProvider::daTonTai);
    }

    /**
     * Một tài khoản <b>đã có chủ</b> trong realm: không còn treo {@code UPDATE_PASSWORD}.
     *
     * <p>Đây là hình dạng của tài khoản dựng qua đăng nhập Google/Zalo (chưa có mật khẩu, chưa bao
     * giờ có yêu cầu đổi mật khẩu) và của tài khoản đã tự đặt mật khẩu xong. Muốn dựng trạng thái
     * <i>mồ côi</i> thì dùng {@link #seedOrphan}.</p>
     */
    public IdentityAccount seed(String email, boolean hasPassword) {
        IdentityAccount account = new IdentityAccount(UUID.randomUUID().toString(), email, email,
                hasPassword, false, false, clock.instant());
        accounts.put(account.subject(), account);
        return account;
    }

    /**
     * Tài khoản <b>mồ côi</b>: do luồng onboarding lập, chưa có mật khẩu, còn treo
     * {@code UPDATE_PASSWORD} — đúng thứ còn lại khi bước ghi cơ sở dữ liệu hỏng giữa chừng.
     *
     * @param createdAt thời điểm realm tạo tài khoản; đẩy về quá khứ để dựng ca "ngoài cửa sổ"
     */
    public IdentityAccount seedOrphan(String email, Instant createdAt) {
        IdentityAccount account = new IdentityAccount(UUID.randomUUID().toString(), email, email,
                false, false, true, createdAt);
        accounts.put(account.subject(), account);
        return account;
    }

    public void notConfigured() {
        this.configured = false;
    }

    /** Đặt đồng hồ của realm giả. */
    public void useClock(Clock replacement) {
        this.clock = replacement;
    }

    /** Lời gọi ghi kế tiếp ném ngoại lệ này rồi tự xoá bẫy. */
    public void failNextWith(RuntimeException error) {
        this.failNext = error;
    }

    public List<String> created() {
        return List.copyOf(created);
    }

    public Optional<IdentityAccount> bySubject(String subject) {
        return Optional.ofNullable(accounts.get(subject));
    }

    public int size() {
        return accounts.size();
    }

    /**
     * Số liên kết đặt mật khẩu đã đúc ra.
     *
     * <p>Đây là con số đáng canh nhất của cả bản giả: một liên kết đặt mật khẩu đúc cho tài khoản
     * của người khác <b>là</b> lỗ hổng chiếm tài khoản, kể cả khi lượt gọi ấy về sau hỏng ở một
     * bước khác và không ai thấy liên kết trong thân phản hồi.</p>
     */
    public int issuedTokenCount() {
        return tokens.size();
    }

    // -------------------------------------------------------------------------------------
    // Cổng
    // -------------------------------------------------------------------------------------

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Optional<IdentityAccount> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String wanted = email.trim().toLowerCase(java.util.Locale.ROOT);
        return accounts.values().stream()
                .filter(account -> wanted.equals(account.email()))
                .findFirst()
                .map(FakeIdentityProvider::daTonTai);
    }

    /**
     * <b>Một lượt TRA không bao giờ là một lượt TẠO.</b>
     *
     * <p>Bản giả từng trả về thẳng đối tượng đang giữ trong map, nên một tài khoản do
     * {@link #createAccount} dựng ra giữ {@code justCreated = true} <b>mãi mãi</b> — kể cả ở những
     * lượt tra hàng giờ sau. Adapter thật làm ngược lại: {@code toAccount(node, false)}, vì
     * Keycloak trả về một người dùng chứ không trả về một sự kiện.</p>
     *
     * <p>Sai lệch ấy không vô hại: nó làm mọi phép kiểm dựa trên {@code justCreated()} <b>xanh
     * nhầm</b> ở đúng ca chúng sinh ra để canh — "định danh này đã có chủ". Đúng cái bẫy mà javadoc
     * của lớp này cảnh báo: test xanh trên một trạng thái mà realm thật trả lời khác.</p>
     */
    private static IdentityAccount daTonTai(IdentityAccount account) {
        return account.justCreated()
                ? new IdentityAccount(account.subject(), account.username(), account.email(),
                        account.hasPassword(), false, account.awaitingInitialPassword(),
                        account.createdAt())
                : account;
    }

    @Override
    public IdentityAccount createAccount(NewIdentityAccount request) {
        trip();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        // duplicateEmailsAllowed: false — realm that se tra 409 o day.
        Optional<IdentityAccount> existing = findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Realm that: createUser luon kem requiredActions = [UPDATE_PASSWORD].
        IdentityAccount account = new IdentityAccount(UUID.randomUUID().toString(), email, email,
                false, true, true, clock.instant());
        accounts.put(account.subject(), account);
        created.add(account.subject());
        return account;
    }

    @Override
    public Optional<SetPasswordLink> issueSetPasswordLink(IdentityAccount account) {
        if (account.hasPassword()) {
            return Optional.empty();
        }
        String token = "token-" + UUID.randomUUID();
        tokens.put(token, account.subject());
        return Optional.of(new SetPasswordLink(
                "https://giapha.test/dat-mat-khau?token=" + token,
                Instant.now().plus(Duration.ofMinutes(30))));
    }

    @Override
    public void completeSetPassword(String token, String rawPassword) {
        trip();
        String subject = tokens.get(token);
        if (subject == null) {
            throw new SetPasswordNotAllowedException("Token khong hop le");
        }
        IdentityAccount account = accounts.get(subject);
        if (account.hasPassword()) {
            throw new SetPasswordNotAllowedException("Tai khoan nay da co mat khau");
        }
        // GO DAU UPDATE_PASSWORD, y het KeycloakIdentityProviderAdapter.completeSetPassword ->
        // api.clearUpdatePasswordAction(subject). Quen ve nay la de mot tai khoan da co chu mang
        // mai dau hieu cua tai khoan mo coi — va bai kiem "van bi tu choi" se xanh vi an may.
        accounts.put(subject, new IdentityAccount(account.subject(), account.username(),
                account.email(), true, false, false, account.createdAt()));
    }

    private void trip() {
        if (failNext != null) {
            RuntimeException boom = failNext;
            failNext = null;
            throw boom;
        }
        if (!configured) {
            throw new IdentityProviderException("Cong danh tinh chua duoc cau hinh");
        }
    }
}
