package vn.giapha.clan.application;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.giapha.membership.application.CouncilAccountCreated;
import vn.giapha.membership.application.CouncilAccountService;
import vn.giapha.membership.application.CreateCouncilAccountCommand;

/**
 * Bước "tài khoản Hội đồng" của lối khởi tạo — <b>uỷ thác trọn</b> cho
 * {@code membership.CouncilAccountService}.
 *
 * <p>Mỏng có chủ ý. Tài khoản, vai trò, liên kết đặt mật khẩu là việc của {@code membership}, và
 * mọi luật của nó (email hoặc số điện thoại, "định danh đã có chủ thì dừng", thứ tự Keycloak trước
 * CSDL sau) sống ở đó. Lớp này tồn tại để controller của {@code clan} đi đúng chiều
 * {@code api → application} thay vì với thẳng sang context khác từ tầng api.</p>
 *
 * <p><b>Không {@code @Transactional}</b>: bên được gọi có lời gọi mạng tới Keycloak và tự giữ ranh
 * giới transaction của mình; bọc thêm một transaction ở đây là giữ một kết nối CSDL suốt chuyến đi
 * mạng — đúng điều {@code PasswordResetGrantWriter} sinh ra để tránh.</p>
 */
@Service
public class ClanCouncilService {

    private final CouncilAccountService councilAccounts;

    public ClanCouncilService(CouncilAccountService councilAccounts) {
        this.councilAccounts = councilAccounts;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public CouncilAccountCreated createCouncilAccount(String displayName, String loginId,
                                                      UUID personId) {
        return councilAccounts.create(new CreateCouncilAccountCommand(displayName, loginId,
                personId, "Khoi tao dong ho: lap tai khoan Hoi dong Toc bieu"));
    }
}
