package vn.giapha.membership.domain.port;

/**
 * Một tài khoản bên nhà cung cấp danh tính (Keycloak), nhìn từ phía miền.
 *
 * <h2>Cố ý KHÔNG mang mật khẩu, không mang credential, không mang vai trò</h2>
 * Miền chỉ cần đúng ba điều: định danh bất biến để nối sang {@code app_user}
 * ({@link #subject()} — chính là claim {@code sub}), thứ người ta gõ khi đăng nhập
 * ({@link #username()} / {@link #email()}), và <b>đã có mật khẩu hay chưa</b>. Điều cuối là thứ
 * quyết định có phát liên kết đặt mật khẩu hay không, nên nó phải đi cùng tài khoản chứ không phải
 * một lời gọi thứ hai mà ai cũng có thể quên.
 *
 * @param subject     claim {@code sub} — khoá nối duy nhất sang {@code app_user.keycloak_sub}
 * @param username    tên đăng nhập trong realm
 * @param email       địa chỉ thư; dữ liệu Tầng 3, không bao giờ ghi vào {@code audit_log}
 * @param hasPassword đã có credential mật khẩu hay chưa. {@code false} với tài khoản vừa tạo và
 *                    với tài khoản chỉ đăng nhập bằng Google/Zalo.
 * @param justCreated lời gọi vừa rồi có thực sự tạo mới tài khoản này không. Dùng cho log và cho
 *                    ca "bấm hai lần" — <b>không</b> dùng để quyết định phát liên kết, vì một lần
 *                    thử lại sau khi ghép hỏng sẽ tìm thấy tài khoản cũ và vẫn phải phát được.
 */
public record IdentityAccount(String subject, String username, String email,
                              boolean hasPassword, boolean justCreated) {
}
