package vn.giapha.membership.application.command;

/**
 * Đăng ký tài khoản bằng mã mời dòng họ.
 *
 * <p><b>Mã được kiểm TRƯỚC khi tài khoản được tạo</b>, không phải tạo rồi mới hỏi (design 07 §1.3).
 * Tạo trước thì một lần gõ sai mã cũng để lại một tài khoản Keycloak mồ côi, và realm đặt
 * {@code duplicateEmailsAllowed: false} nên lần thử lại của chính người ấy sẽ hỏng.</p>
 *
 * @param code        mã thô người dùng gõ; được chuẩn hoá rồi băm, không bao giờ được log
 * @param email       địa chỉ thư người dùng tự khai — định danh đăng nhập ở Keycloak
 * @param displayName tên hiển thị tự khai; <b>không</b> phải tên trong phả, và không được dùng để
 *                    tự động suy ra nhân khẩu nào — việc ghép là của đơn tự nhận và của Trưởng chi
 * @param clientId    định danh thô của người gọi (địa chỉ IP), cho bộ đếm giới hạn tần suất
 */
public record RegisterWithClanInviteCommand(String code, String email, String displayName,
                                            String clientId) {
}
