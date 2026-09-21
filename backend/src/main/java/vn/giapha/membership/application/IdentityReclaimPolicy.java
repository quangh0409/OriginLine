package vn.giapha.membership.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.IdentityAccount;

/**
 * <b>Chỗ duy nhất</b> trả lời câu hỏi: người gọi <i>không trình token</i> này có được phép thao tác
 * trên tài khoản Keycloak vừa tìm thấy không.
 *
 * <h2>Vì sao câu hỏi này khó, và vì sao nó phải nằm ở đúng một chỗ</h2>
 * Hai lối vào hệ thống — mã mời cá nhân ({@code InvitationService}) và mã mời dòng họ
 * ({@code ClanInviteService}) — đều nhận một chuỗi người dùng <b>tự gõ</b> vào ô "email hoặc số
 * điện thoại". Chuỗi ấy không chứng minh được quyền sở hữu. Nếu luật này bị chép ra hai bản, một
 * bản sẽ lệch ở lần sửa đầu tiên, và cái lệch ấy là một lỗ hổng chiếm tài khoản chứ không phải một
 * khác biệt hành vi.
 *
 * <h2>Ba mệnh đề, phải đồng thời đúng</h2>
 * <ol>
 *   <li><b>Tài khoản vừa được lập trong chính lượt gọi này</b> ({@code justCreated}) — đi thẳng,
 *       không cần gì thêm; hoặc</li>
 *   <li><b>realm còn treo {@code UPDATE_PASSWORD}</b>, dấu vết mà <i>chỉ</i> luồng onboarding của
 *       hệ thống này để lại và bị gỡ ngay sau lần đặt mật khẩu đầu tiên — nên một tài khoản dựng
 *       qua đăng nhập Google/Zalo không bao giờ có nó; <b>và</b></li>
 *   <li><b>chưa có dòng {@code app_user} nào cho {@code sub} ấy</b> — nghĩa là chưa ai nhận tài
 *       khoản này về mình: chưa gắn nhân khẩu, chưa đăng ký xong, chưa từng đăng nhập vào ứng
 *       dụng; <b>và</b></li>
 *   <li><b>tài khoản còn trong cửa sổ mồ côi</b> — xem dưới.</li>
 * </ol>
 *
 * <h2>Trạng thái "mồ côi" là gì, và vì sao phải cứu nó</h2>
 * Keycloak và Postgres là hai hệ thống không có transaction chung. Thứ tự thao tác cố ý đặt lượt
 * tạo tài khoản <i>trước</i> lượt ghi cơ sở dữ liệu, nên khi bước ghi hỏng giữa chừng (mất kết nối
 * CSDL), cái còn lại là một tài khoản Keycloak <b>không ai sở hữu</b>: không mật khẩu, không dòng
 * {@code app_user}, còn nguyên {@code UPDATE_PASSWORD}. Không cứu trạng thái ấy thì người được mời
 * bấm lại và bị chính phép chặn từ chối — <b>vĩnh viễn</b>, vì lần nào cũng vậy — và một cụ cầm tờ
 * phiếu mời thật phải chờ Hội đồng vào realm xoá tay.
 *
 * <h2>Vì sao đóng khung thời gian</h2>
 * Một tài khoản mồ côi nằm đó vô hạn là một mục tiêu đứng yên: bất kỳ ai đoán đúng địa chỉ thư đều
 * đòi lại được nó, mãi mãi. Cửa sổ mặc định bằng đúng <b>hạn của liên kết đặt mật khẩu</b>
 * ({@code giapha.keycloak.admin.link-ttl}, 30 phút): tài khoản mồ côi được đòi lại đúng khoảng
 * thời gian mà liên kết đáng lẽ nó nhận được sẽ còn sống. Một lần bấm lại thật xảy ra sau vài giây;
 * ngoài cửa sổ ấy thì lối đi đúng là báo Trưởng chi.
 *
 * <p><b>Realm không nói {@code createdTimestamp}</b> ⇒ coi như <i>ngoài</i> cửa sổ. Thiếu dữ kiện
 * thì chọn vế an toàn, không chọn vế tiện.</p>
 *
 * <h2>Lỗ hổng còn lại, đã biết và đã cân</h2>
 * Một quản trị realm có thể <i>tự tay</i> gắn {@code UPDATE_PASSWORD} lên một tài khoản có chủ. Ca
 * ấy chỉ lọt qua được nếu tài khoản đó <b>đồng thời</b> không có credential mật khẩu nào (nếu có,
 * {@code issueSetPasswordLink} đã từ chối), chưa từng dùng ứng dụng (không có {@code app_user}), và
 * việc gắn dấu diễn ra trong cửa sổ 30 phút vừa rồi. Ba điều kiện ấy cùng lúc là một thao tác quản
 * trị bất thường, không phải một lối tấn công từ ngoài.
 */
@Service
public class IdentityReclaimPolicy {

    private static final Logger log = LoggerFactory.getLogger(IdentityReclaimPolicy.class);

    private final AppUserRepository appUsers;
    private final Duration orphanWindow;
    private final Clock clock;

    // @Autowired la BAT BUOC: lop nay co hai constructor, va Spring khong tu chon duoc.
    @Autowired
    public IdentityReclaimPolicy(AppUserRepository appUsers,
                                 @Value("${giapha.keycloak.admin.link-ttl:30m}")
                                 Duration orphanWindow) {
        this(appUsers, orphanWindow, Clock.systemUTC());
    }

    /** Lối dựng cho test: đồng hồ điều khiển được, không cần chờ 30 phút thật. */
    public IdentityReclaimPolicy(AppUserRepository appUsers, Duration orphanWindow, Clock clock) {
        this.appUsers = appUsers;
        this.orphanWindow = orphanWindow;
        this.clock = clock;
    }

    /**
     * Người gọi không trình token có được thao tác tiếp trên {@code account} không.
     *
     * @return {@code true} khi tài khoản vừa được lập, hoặc là một tài khoản mồ côi còn trong cửa sổ
     */
    public boolean mayClaim(IdentityAccount account) {
        if (account.justCreated()) {
            return true;
        }
        if (!account.awaitingInitialPassword()) {
            // Dau UPDATE_PASSWORD da bi go, hoac chua bao gio co: tai khoan nay khong phai do luong
            // onboarding nay de lai. Day la ca tai khoan Google/Zalo.
            return false;
        }
        if (appUsers.byKeycloakSub(account.subject()).isPresent()) {
            // Da co nguoi nhan tai khoan nay ve minh — ke ca khi ho chua dat mat khau. Day dung la
            // ca tan cong goc: nguoi duoc moi da nhan loi moi nhung chua bam vao lien ket.
            return false;
        }
        if (!trongCuaSo(account.createdAt())) {
            log.info("Tai khoan mo coi {} da ngoai cua so {} — khong doi lai duoc nua",
                    account.subject(), orphanWindow);
            return false;
        }
        log.info("Tai khoan {} la tai khoan mo coi cua chinh luong onboarding (chua co app_user,"
                + " con treo UPDATE_PASSWORD, trong cua so {}) — cho doi lai",
                account.subject(), orphanWindow);
        return true;
    }

    private boolean trongCuaSo(Instant createdAt) {
        if (createdAt == null) {
            // Realm khong noi thi coi nhu ngoai cua so: thieu du kien thi chon ve an toan.
            return false;
        }
        return !createdAt.plus(orphanWindow).isBefore(clock.instant());
    }
}
