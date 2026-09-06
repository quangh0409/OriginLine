package vn.giapha.notification.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.RecipientDirectory;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Phân giải "hộp thư này là của ai" từ JWT: {@code keycloak_sub → app_user → person}.
 *
 * <p>Toàn bộ API của context này thao tác trên dữ liệu của <b>chính người đang đăng nhập</b>. Hợp
 * đồng OpenAPI cố ý không có tham số {@code userId} — hộp thư người khác không phải thứ để lộ qua
 * tham số, và một tham số như vậy sẽ vĩnh viễn là chỗ để quên kiểm tra quyền.</p>
 *
 * <p>Tài khoản có JWT hợp lệ nhưng <b>chưa được ghép vào cây</b> ({@code app_user.person_id} rỗng)
 * thì không có hộp thư: mọi khoá của {@code notification_inbox} và {@code notification_log} đều là
 * {@code person_id}. Trả 403 kèm mã riêng để giao diện nói được câu đúng ("tài khoản chưa được
 * ghép với nhân khẩu, liên hệ Hội đồng Tộc biểu") thay vì một màn hình trắng.</p>
 */
@Service
public class CurrentAccountService {

    /** Mã lỗi ổn định cho giao diện — xem {@code Problem.code} của RFC 7807. */
    public static final String CODE_UNLINKED_ACCOUNT = "ACCOUNT_NOT_LINKED";

    private final RecipientDirectory recipients;

    public CurrentAccountService(RecipientDirectory recipients) {
        this.recipients = recipients;
    }

    /** @throws ForbiddenException khi chưa đăng nhập hoặc tài khoản chưa ghép với nhân khẩu */
    public Recipient require() {
        return find().orElseThrow(() -> new ForbiddenException(CODE_UNLINKED_ACCOUNT,
                "Tai khoan chua duoc ghep voi nhan khau trong gia pha nen chua co hop thu."));
    }

    public Optional<Recipient> find() {
        return CurrentUserProvider.current()
                .map(CurrentUser::keycloakSub)
                .flatMap(recipients::byKeycloakSub);
    }
}
