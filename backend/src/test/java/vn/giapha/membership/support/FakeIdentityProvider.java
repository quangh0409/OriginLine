package vn.giapha.membership.support;

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

    private RuntimeException failNext;

    // -------------------------------------------------------------------------------------
    // Dựng cảnh
    // -------------------------------------------------------------------------------------

    /** Một tài khoản đã có sẵn trong realm, kèm hay không kèm mật khẩu. */
    public IdentityAccount seed(String email, boolean hasPassword) {
        IdentityAccount account = new IdentityAccount(UUID.randomUUID().toString(), email, email,
                hasPassword, false);
        accounts.put(account.subject(), account);
        return account;
    }

    public void notConfigured() {
        this.configured = false;
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
                .findFirst();
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
        IdentityAccount account = new IdentityAccount(UUID.randomUUID().toString(), email, email,
                false, true);
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
        accounts.put(subject, new IdentityAccount(account.subject(), account.username(),
                account.email(), true, false));
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
