package vn.giapha.membership.domain.port;

import java.time.Instant;

/**
 * Một tài khoản bên nhà cung cấp danh tính (Keycloak), nhìn từ phía miền.
 *
 * <h2>Cố ý KHÔNG mang mật khẩu, không mang credential, không mang vai trò</h2>
 * Miền chỉ cần những sự kiện đủ để trả lời một câu hỏi duy nhất — <b>"có được phát liên kết đặt
 * mật khẩu cho tài khoản này không"</b> — cộng với định danh bất biến để nối sang {@code app_user}
 * ({@link #subject()} — chính là claim {@code sub}).
 *
 * <h2>Bản ghi này chỉ chở SỰ KIỆN, không chở PHÁN QUYẾT</h2>
 * Không có trường nào tên kiểu {@code canIssueLink}. Luật ghép các sự kiện ấy lại nằm ở
 * {@code IdentityReclaimPolicy}, vì một trong các điều kiện — "đã có dòng {@code app_user} chưa" —
 * nằm ở cơ sở dữ liệu của <i>hệ thống này</i> chứ không ở realm. Nhét phán quyết vào đây sẽ buộc
 * adapter hạ tầng phải biết về {@code app_user}.
 *
 * @param subject     claim {@code sub} — khoá nối duy nhất sang {@code app_user.keycloak_sub}
 * @param username    tên đăng nhập trong realm
 * @param email       địa chỉ thư; dữ liệu Tầng 3, không bao giờ ghi vào {@code audit_log}
 * @param hasPassword đã có credential mật khẩu hay chưa. {@code false} với tài khoản vừa tạo và
 *                    với tài khoản chỉ đăng nhập bằng Google/Zalo.
 * @param justCreated lời gọi vừa rồi có thực sự tạo mới tài khoản này không. Dùng cho log và cho
 *                    ca "bấm hai lần". Một lượt <b>TRA</b> không bao giờ là một lượt <b>TẠO</b>:
 *                    adapter đặt {@code false} ở mọi lối tra, và bản giả trong test phải làm y hệt
 *                    — bản giả cũ trả về chính đối tượng đã lưu nên trường này đúng mãi mãi, và
 *                    mọi phép canh dựa vào nó xanh vì ăn may.
 * @param awaitingInitialPassword realm còn treo yêu cầu bắt buộc {@code UPDATE_PASSWORD}. Đây là
 *                    dấu vết <b>chỉ luồng onboarding của hệ thống này để lại</b>: {@code createUser}
 *                    đặt nó, và {@code completeSetPassword} gỡ nó ngay sau lần đặt mật khẩu đầu
 *                    tiên. Một tài khoản dựng qua đăng nhập Google/Zalo <b>không bao giờ</b> có nó.
 * @param createdAt   thời điểm realm tạo tài khoản ({@code createdTimestamp}), hoặc {@code null}
 *                    nếu realm không nói. Dùng để đóng khung thời gian cho trạng thái mồ côi — xem
 *                    {@code IdentityReclaimPolicy}.
 */
public record IdentityAccount(String subject, String username, String email,
                              boolean hasPassword, boolean justCreated,
                              boolean awaitingInitialPassword, Instant createdAt) {
}
