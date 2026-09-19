package vn.giapha.membership.domain.port;

import java.util.Optional;

/**
 * Cổng sang <b>nhà cung cấp danh tính</b> — nơi tài khoản đăng nhập thật sự sống (Keycloak).
 *
 * <h2>Vì sao context {@code membership} cần cổng này</h2>
 * Cho tới trước khi có nó, backend là resource server thuần: tiêu thụ JWT, không tạo người dùng,
 * không phát token. Hệ quả là <b>người được mời phải đã có tài khoản mới nhận được lời mời</b> —
 * đúng cái vòng luẩn quẩn mà luồng mời sinh ra để phá, vì realm đặt
 * {@code registrationAllowed: false} và không có lối đăng ký nào khác.
 *
 * <p>Cổng này mở đúng một lối và không mở hơn: <b>tìm · tạo · phát liên kết đặt mật khẩu ·
 * nhận mật khẩu người dùng tự đặt</b>. Không xoá tài khoản, không gán vai trò, không liệt kê người
 * dùng, không đổi mật khẩu của một tài khoản đã có mật khẩu. Tài khoản dịch vụ ở phía adapter vì
 * thế chỉ cần đúng một vai client ({@code realm-management:manage-users}) — xem
 * {@code KeycloakIdentityProviderAdapter}.</p>
 *
 * <h2>Hai hệ thống, không có transaction chung</h2>
 * Mọi phương thức ở đây đều gọi qua mạng tới một hệ thống <b>ngoài</b> transaction của Postgres.
 * Vì vậy cả ba phương thức ghi đều được thiết kế để <b>lặp lại được</b>: gọi hai lần cho cùng một
 * người phải ra cùng một kết quả, không sinh bản sao. Đó là điều cho phép
 * {@code InvitationService.accept} đặt lần ghi CSDL xuống <i>cuối cùng</i> và coi nó là bước duy
 * nhất không đảo ngược được.
 */
public interface IdentityProviderPort {

    /**
     * Đã có đủ cấu hình để tạo tài khoản chưa.
     *
     * <p>Thiếu bí mật của client dịch vụ thì cổng <b>tự tắt</b> chứ không làm hỏng ứng dụng — cùng
     * khuôn mẫu với Web Push. Lối nhận lời mời bằng token sẵn có vẫn chạy; chỉ lối "chưa có tài
     * khoản" là trả về {@code 503}.</p>
     */
    boolean isConfigured();

    /**
     * Tìm tài khoản theo email — <b>gọi trước khi tạo, luôn luôn</b>.
     *
     * <p>Người được mời có thể đã có tài khoản từ một email cũ, hoặc vừa bấm nút hai lần. Tạo mà
     * không tìm trước thì realm từ chối vì {@code duplicateEmailsAllowed: false} và người dùng nhận
     * một lỗi 500 ở đúng giây đầu tiên họ dùng hệ thống.</p>
     */
    Optional<IdentityAccount> findByEmail(String email);

    /**
     * Tạo tài khoản <b>không có mật khẩu</b>, kèm yêu cầu bắt buộc đặt mật khẩu.
     *
     * @throws IdentityProviderException khi nhà cung cấp danh tính từ chối hoặc không với tới được
     */
    IdentityAccount createAccount(NewIdentityAccount request);

    /**
     * Phát liên kết một lần để chính chủ đặt mật khẩu.
     *
     * <p>Trả {@link Optional#empty()} khi tài khoản <b>đã có mật khẩu</b> — người ấy đăng nhập như
     * bình thường, và phát liên kết cho họ là mở một lối đổi mật khẩu cho bất kỳ ai cầm mã mời.</p>
     */
    Optional<SetPasswordLink> issueSetPasswordLink(IdentityAccount account);

    /**
     * Ghi mật khẩu <b>do chính người dùng chọn</b> vào nhà cung cấp danh tính.
     *
     * <p>Hệ thống này không sinh mật khẩu, không lưu mật khẩu và không đọc lại được mật khẩu; chuỗi
     * ký tự đi thẳng qua đây tới Keycloak, nơi chính sách mật khẩu của realm được áp dụng và nơi nó
     * được băm. Mọi ràng buộc về độ mạnh là của realm, không phải của lớp này.</p>
     *
     * @throws SetPasswordNotAllowedException token sai/quá hạn, hoặc tài khoản đã có mật khẩu
     * @throws IdentityProviderException      realm từ chối (ví dụ vi phạm chính sách mật khẩu)
     */
    void completeSetPassword(String token, String rawPassword);
}
