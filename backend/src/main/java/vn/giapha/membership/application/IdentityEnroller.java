package vn.giapha.membership.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.LoginIdentifier;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.NewIdentityAccount;
import vn.giapha.shared.exception.DomainException;

/**
 * <b>Chỗ duy nhất</b> lập một tài khoản đăng nhập cho người chưa có: tìm-hoặc-tạo, theo email
 * <i>hoặc</i> số điện thoại.
 *
 * <h2>Vì sao tách ra khỏi hai service gọi nó</h2>
 * Hai lối vào hệ thống đều cần đúng việc này: {@code InvitationService} (mã cá nhân) và
 * {@code ClanInviteService} (mã dòng họ). Bản dựng đầu chép đoạn "tìm trước, tạo sau" sang cả hai,
 * và hai bản chép đã <b>lệch nhau ngay lần sửa đầu tiên</b>: lối mã dòng họ được mở cho số điện
 * thoại còn lối mã cá nhân thì không — tức là đúng nhóm người mà mã cá nhân sinh ra để phục vụ
 * (các cụ lớn tuổi, thường không có email) lại là nhóm duy nhất không dùng được.
 *
 * <h2>TÌM TRƯỚC, TẠO SAU — luôn luôn</h2>
 * Người ấy có thể đã có tài khoản từ một lần trước, hoặc vừa bấm hai lần, hoặc đang thử lại sau
 * một lần ghép hỏng. Tạo mà không tìm trước thì realm từ chối vì trùng
 * ({@code duplicateEmailsAllowed: false}, và tên đăng nhập thì luôn duy nhất) và người dùng nhận
 * một lỗi 500 ở đúng giây đầu tiên họ dùng hệ thống.
 *
 * <h2>Lớp này KHÔNG ghi một dòng CSDL nào</h2>
 * Nó chỉ chạm Keycloak. Đó là chủ ý của cả thiết kế thứ tự thao tác: thao tác mạng nằm
 * <b>ngoài</b> transaction Postgres, nên mọi hỏng hóc rơi về một trạng thái lành — mã mời chưa bị
 * đốt, lượt dùng chưa bị tiêu, và người dùng bấm lại được.
 */
@Service
public class IdentityEnroller {

    private static final Logger log = LoggerFactory.getLogger(IdentityEnroller.class);

    private final IdentityProviderPort identityProvider;

    public IdentityEnroller(IdentityProviderPort identityProvider) {
        this.identityProvider = identityProvider;
    }

    /**
     * Đọc thứ người dùng tự khai thành một định danh đăng nhập.
     *
     * @throws DomainException {@code VALIDATION_FAILED} khi không đọc được thành email lẫn số máy
     */
    public LoginIdentifier readIdentifier(String raw) {
        try {
            return LoginIdentifier.of(raw);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
    }

    /**
     * Tìm-hoặc-tạo tài khoản Keycloak cho {@code login}.
     *
     * <p>Phép tra đi theo <b>kiểu định danh</b>: email thì tra thuộc tính {@code email}, số điện
     * thoại thì tra tên đăng nhập. Tra nhầm trường sẽ luôn ra rỗng và lớp này sẽ tạo một tài khoản
     * thứ hai cho cùng một người — rồi một trong hai được ghép vào phả còn cái kia thì không.</p>
     *
     * @throws IdentityProviderException khi cổng danh tính chưa được cấu hình hoặc không với tới được
     */
    public IdentityAccount findOrCreate(LoginIdentifier login, String displayName) {
        if (!identityProvider.isConfigured()) {
            // Noi thang la chua cau hinh, khong gia vo la loi cua nguoi dung: ho khong sua duoc.
            throw new IdentityProviderException(
                    "He thong chua duoc cau hinh de lap tai khoan moi");
        }
        return timTheoKieu(login)
                .orElseGet(() -> {
                    log.info("Lap tai khoan dang nhap moi bang dinh danh kieu {}", login.kind());
                    return identityProvider.createAccount(new NewIdentityAccount(
                            login.value(), login.emailOrNull(), displayName));
                });
    }

    private java.util.Optional<IdentityAccount> timTheoKieu(LoginIdentifier login) {
        return login.isEmail()
                ? identityProvider.findByEmail(login.value())
                : identityProvider.findByUsername(login.value());
    }
}
