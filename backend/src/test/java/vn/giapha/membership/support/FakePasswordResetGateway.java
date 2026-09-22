package vn.giapha.membership.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.PasswordRejectedException;
import vn.giapha.membership.domain.port.PasswordResetPort;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.membership.infrastructure.keycloak.SetPasswordTokenCodec;

/**
 * Bản trong bộ nhớ của nửa Keycloak thuộc luồng <b>cấp lại liên kết đặt mật khẩu</b>.
 *
 * <h2>Dùng CODEC THẬT, không tự bịa token</h2>
 * Đây là khác biệt quan trọng nhất so với một bản giả thông thường. Nếu bản giả tự sinh chuỗi
 * {@code "token-1"} rồi tra trong một {@code Map}, thì hai tính chất đáng canh nhất của luồng này
 * <b>không còn được canh</b>:
 * <ul>
 *   <li><b>Hết hạn thì vô dụng</b> — hạn nằm trong chính token và do chữ ký bảo vệ, không nằm
 *       trong một {@code Map} mà bản giả tự kiểm;</li>
 *   <li><b>Token của luồng mời không dùng được ở đây, và ngược lại</b> — điều đó do hai khoá ký
 *       khác nhau bảo đảm, và một bản giả dùng {@code Map} sẽ luôn xanh bất kể khoá.</li>
 * </ul>
 * Vì vậy lớp này gọi thẳng {@link SetPasswordTokenCodec#forPasswordReset} của mã sản xuất.
 *
 * <p>{@link #tuaDongHo(Duration)} đẩy đồng hồ của cả cổng lên phía trước để dựng ca "liên kết đã
 * hết hạn" mà không phải chờ nửa giờ thật.</p>
 */
public final class FakePasswordResetGateway implements PasswordResetPort {

    /** Bí mật giả của client dịch vụ; chỉ cần khác chuỗi rỗng để codec dẫn xuất được khoá ký. */
    public static final String SECRET = "bi-mat-client-dich-vu-cua-test";

    private static final String BASE_URL = "https://giapha.test/vi/dat-mat-khau?token=";

    private final SetPasswordTokenCodec codec = SetPasswordTokenCodec.forPasswordReset(SECRET);

    /** Realm giả, khoá là {@code sub}. */
    private final Map<String, IdentityAccount> accounts = new LinkedHashMap<>();

    /** Mật khẩu đã ghi đè: {@code sub} → chuỗi. Bằng chứng "ai bị đổi mật khẩu". */
    private final Map<String, String> passwords = new LinkedHashMap<>();

    /**
     * Số liên kết đã đúc ra.
     *
     * <p>Đây là con số đáng canh nhất của cả bản giả: một liên kết đặt lại mật khẩu đúc cho tài
     * khoản của người khác <b>là</b> lỗ hổng chiếm tài khoản, kể cả khi lượt gọi ấy về sau hỏng ở
     * một bước khác và không ai nhìn thấy liên kết trong thân phản hồi.</p>
     */
    private int issuedLinkCount;

    private Duration offset = Duration.ZERO;

    private boolean configured = true;

    /** Độ dài tối thiểu mà "realm" chấp nhận — dựng ca vi phạm chính sách mật khẩu. */
    private int minPasswordLength;

    /**
     * Một {@code PasswordResetRedeemer} <b>không phục vụ ca nào</b> — dành cho các bộ test chỉ quan
     * tâm tới luồng đặt mật khẩu <i>lần đầu</i>.
     *
     * <h2>Ba cộng tác viên {@code null}, và vì sao chúng không bao giờ bị chạm tới</h2>
     * {@code completeIfResetToken} hỏi {@link #readGrantRef} <b>trước tiên</b>. Cổng giả ở đây chưa
     * cấp lượt nào, và quan trọng hơn: token của luồng mời được ký bằng một <b>khoá khác</b>, nên
     * phép kiểm chữ ký luôn trả rỗng và phương thức trả {@code false} ngay dòng đầu. Kho lưu, kho
     * tài khoản và nhật ký chỉ được dùng <i>sau</i> dòng ấy.
     *
     * <p>Nếu một ca test nào đó làm {@code NullPointerException} nổ ở đây thì nó <b>không</b> phải
     * một ca của luồng mời: nó vừa đưa vào một token của luồng cấp lại, và chỗ đúng của nó là
     * {@code PasswordResetFlowIT}.</p>
     */
    public static vn.giapha.membership.application.PasswordResetRedeemer khongDungLuongCapLai() {
        return new vn.giapha.membership.application.PasswordResetRedeemer(
                null, null, new FakePasswordResetGateway(), null);
    }

    // -------------------------------------------------------------------------------------
    // Dựng cảnh
    // -------------------------------------------------------------------------------------

    /**
     * Tài khoản lập bằng <b>số điện thoại</b>: có tên đăng nhập, <b>không có email</b>, đã có mật
     * khẩu. Đây là hình dạng của đúng nhóm người mà luồng cấp lại phục vụ.
     */
    public void seedPhoneAccount(String subject, String username) {
        accounts.put(subject, new IdentityAccount(subject, username, null, true, false, false,
                now()));
    }

    /**
     * Tài khoản có <b>địa chỉ thư</b> — nhóm đã tự lấy lại mật khẩu được bằng thư của Keycloak,
     * nên luồng cấp lại phải từ chối. Cũng là hình dạng của mọi tài khoản Google/Zalo.
     */
    public void seedEmailAccount(String subject, String email) {
        accounts.put(subject, new IdentityAccount(subject, email, email, true, false, false,
                now()));
    }

    /** Đẩy đồng hồ của cổng lên phía trước. */
    public void tuaDongHo(Duration amount) {
        offset = offset.plus(amount);
    }

    public void notConfigured() {
        this.configured = false;
    }

    public void requirePasswordLength(int min) {
        this.minPasswordLength = min;
    }

    public int issuedLinkCount() {
        return issuedLinkCount;
    }

    public Optional<String> passwordOf(String subject) {
        return Optional.ofNullable(passwords.get(subject));
    }

    public void reset() {
        accounts.clear();
        passwords.clear();
        issuedLinkCount = 0;
        offset = Duration.ZERO;
        configured = true;
        minPasswordLength = 0;
    }

    // -------------------------------------------------------------------------------------
    // Cổng
    // -------------------------------------------------------------------------------------

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Optional<IdentityAccount> findBySubject(String keycloakSub) {
        requireConfigured();
        return Optional.ofNullable(accounts.get(keycloakSub));
    }

    @Override
    public SetPasswordLink issueResetLink(UUID grantId, Instant expiresAt) {
        requireConfigured();
        Instant now = now();
        if (!expiresAt.isAfter(now)) {
            throw new IdentityProviderException("Han cua lien ket da qua");
        }
        SetPasswordTokenCodec.Minted minted =
                codec.mint(grantId.toString(), Duration.between(now, expiresAt), now);
        issuedLinkCount++;
        return new SetPasswordLink(BASE_URL + minted.token(), minted.expiresAt());
    }

    @Override
    public Optional<UUID> readGrantRef(String token) {
        if (!configured) {
            return Optional.empty();
        }
        return codec.verify(token, now()).map(UUID::fromString);
    }

    @Override
    public void overwritePassword(String keycloakSub, String rawPassword) {
        requireConfigured();
        if (!accounts.containsKey(keycloakSub)) {
            throw new IdentityProviderException("Tai khoan khong con ton tai: " + keycloakSub);
        }
        if (rawPassword.length() < minPasswordLength) {
            // Realm that tra 400 o day va CHUA ghi gi ca — dung ca ma PasswordResetRedeemer phai
            // tra lien ket ve trang thai chua dung.
            throw new PasswordRejectedException("Mat khau qua ngan theo chinh sach cua realm");
        }
        passwords.put(keycloakSub, rawPassword);
    }

    /** Đồng hồ của cổng giả, kể cả phần đã bị tua. */
    private Instant now() {
        return Clock.systemUTC().instant().plus(offset);
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IdentityProviderException("Cong cap lai mat khau chua duoc cau hinh");
        }
    }
}
