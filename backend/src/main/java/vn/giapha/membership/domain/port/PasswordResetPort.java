package vn.giapha.membership.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng sang nhà cung cấp danh tính cho <b>đúng một việc nguy hiểm</b>: ghi đè mật khẩu của một tài
 * khoản <i>đã có chủ</i>.
 *
 * <h2>Vì sao KHÔNG thêm ba phương thức này vào {@link IdentityProviderPort}</h2>
 * Javadoc của cổng kia nói thẳng nó "mở đúng một lối và không mở hơn": tìm · tạo · phát liên kết
 * đặt mật khẩu <i>lần đầu</i> · nhận mật khẩu người dùng tự đặt — và <b>không đổi mật khẩu của một
 * tài khoản đã có mật khẩu</b>. Đó không phải một câu văn trang trí: {@code InvitationService},
 * {@code ClanInviteService} và {@code IdentityEnroller} đều giữ cổng ấy trong tay, và nếu quyền ghi
 * đè mật khẩu nằm chung ở đó thì ba lớp không liên quan gì tới luồng này đều gọi được nó — chỉ cần
 * một lần ai đó "tiện tay" trong một bản vá về sau.
 *
 * <p>Tách ra thành một cổng riêng khiến năng lực ấy <b>chỉ tới được nơi đã cân nhắc</b>: hai lớp
 * {@code PasswordResetGrantService} (cấp) và {@code PasswordResetRedeemer} (đổi). Đây là phân tách
 * theo <i>ai được cầm</i>, không phải theo gói.</p>
 *
 * <h2>Token của cổng này KHÔNG đọc được bằng codec của luồng mời, và ngược lại</h2>
 * Hai loại token đi vào <b>cùng một endpoint</b> ({@code POST /api/v1/invitations/set-password}) và
 * <b>cùng một màn hình</b> ({@code /dat-mat-khau}) — vì đó là màn hình duy nhất người dùng có, và
 * khuôn địa chỉ ấy đã được đúc vào cấu hình. Phân biệt chúng bằng hình dạng chuỗi là một phép đoán;
 * phân biệt bằng <b>khoá ký khác nhau</b> (chuỗi phân tách miền khác nhau) là một phép chứng minh:
 * một token đặt-mật-khẩu-lần-đầu đưa vào {@link #readGrantRef} luôn trả rỗng, và ngược lại.
 */
public interface PasswordResetPort {

    /**
     * Đã có đủ cấu hình chưa. Thiếu bí mật client dịch vụ thì cổng <b>tự tắt</b> — ứng dụng vẫn
     * khởi động, chỉ luồng này trả {@code 503}.
     */
    boolean isConfigured();

    /**
     * Tra tài khoản theo {@code sub} — khoá lấy từ {@code app_user.keycloak_sub}.
     *
     * <p>Tra theo {@code sub} chứ không theo email hay tên đăng nhập: hai thứ ấy là <i>thuộc tính</i>
     * của tài khoản và đổi được; {@code sub} là thứ đã nối tài khoản với nhân khẩu trong hệ thống
     * này. Tra bằng thuộc tính ở đây là mở lại đúng lối "tự khai định danh" mà
     * {@code IdentityReclaimPolicy} sinh ra để đóng.</p>
     */
    Optional<IdentityAccount> findBySubject(String keycloakSub);

    /**
     * Đúc liên kết một lần trỏ tới lượt cấp {@code grantId}, hết hiệu lực đúng lúc
     * {@code expiresAt}.
     *
     * <h2>Hạn đi vào đây từ bên ngoài, không tự đọc cấu hình</h2>
     * Hạn của token và cột {@code password_reset_grant.expires_at} phải là <b>một</b> giá trị. Nếu
     * mỗi bên tự đọc {@code giapha.keycloak.admin.link-ttl} thì hai lượt đọc cách nhau vài mili
     * giây đã lệch, và tệ hơn: đổi cấu hình giữa hai lượt đọc sẽ tạo ra một token sống lâu hơn
     * lượt cấp, hoặc ngược lại. Lệch kiểu nào cũng khó tra, vì cả hai đều "đúng theo cấu hình".
     *
     * <p>Token mang đúng hai thứ: {@code grantId} và hạn. <b>Không</b> mang {@code sub}, không mang
     * khoá nhân khẩu, không mang vai trò — nó đi qua thanh địa chỉ trình duyệt và, ở luồng này, qua
     * cả một tin nhắn Zalo mà dòng họ không kiểm soát.</p>
     */
    SetPasswordLink issueResetLink(UUID grantId, Instant expiresAt);

    /**
     * Đọc {@code grantId} từ token, nếu token đúng chữ ký của <b>luồng cấp lại</b> và còn hạn.
     *
     * <p>Mọi lý do hỏng — sai định dạng, sai chữ ký, quá hạn, hay đơn giản là token của luồng mời —
     * trả về cùng một {@link Optional#empty()}. Người gọi dùng kết quả rỗng để nói "không phải token
     * của tôi" và chuyển sang luồng kia; nó <b>không</b> được dịch thành một thông báo lỗi riêng,
     * vì phân biệt các lý do hỏng cho người gọi là nói cho kẻ dò biết mình đang sai ở đâu.</p>
     */
    Optional<UUID> readGrantRef(String token);

    /**
     * <b>Ghi đè</b> mật khẩu của một tài khoản có thể đã có mật khẩu.
     *
     * <h2>Đây là phương thức nguy hiểm nhất trong cả context {@code membership}</h2>
     * Gọi nó cho một {@code sub} sai là chiếm tài khoản của một người. Nó <b>không</b> tự kiểm
     * quyền, <b>không</b> tự kiểm "một lần", và không được phép tự kiểm: cả hai phép ấy dựa trên dữ
     * liệu của hệ thống này ({@code branch_assignment}, {@code password_reset_grant}) chứ không của
     * realm. Chỗ duy nhất được gọi nó là {@code PasswordResetRedeemer}, <i>sau khi</i> đã đốt được
     * lượt cấp.
     *
     * @throws PasswordRejectedException  realm từ chối vì chính sách mật khẩu — <b>chưa</b> ghi gì
     * @throws IdentityProviderException  realm không với tới được, hoặc từ chối vì lý do khác
     */
    void overwritePassword(String keycloakSub, String rawPassword);
}
