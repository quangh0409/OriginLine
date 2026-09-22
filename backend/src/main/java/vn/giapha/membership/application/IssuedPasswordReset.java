package vn.giapha.membership.application;

import java.time.Instant;

/**
 * Kết quả của lệnh cấp lại — <b>lần duy nhất liên kết tồn tại ngoài màn hình người cấp</b>.
 *
 * <h2>Đọc kỹ trước khi dùng kiểu này ở bất cứ đâu khác</h2>
 * {@link #url()} không đọc lại được. Nó không nằm trong CSDL (bảng {@code password_reset_grant}
 * không lưu token, kể cả băm), không được ghi vào {@code audit_log}, và không được đưa vào một câu
 * log nào — kể cả ở mức {@code DEBUG}, vì log {@code DEBUG} của môi trường thật vẫn là một tệp nằm
 * trên đĩa. Đây là <b>cùng một khuôn</b> với {@link IssuedInvitation}, và nó là cùng một khuôn vì
 * nó là cùng một loại bí mật.
 *
 * <p>Trưởng chi lỡ tay đóng cửa sổ thì <b>cấp lại</b>; thao tác cấp lại tự thu hồi liên kết cũ. Đó
 * không phải hạn chế của hiện thực mà là chính điều kiện khiến "một lần" có nghĩa.</p>
 *
 * <h2>Vì sao liên kết hiện trên màn hình chứ không được gửi đi</h2>
 * Người nhận <b>không có email</b> — đó là cả lý do luồng này tồn tại; nếu họ có email thì Keycloak
 * đã tự lo bằng "quên mật khẩu" và không cần ai cấp gì cả. Kênh duy nhất hệ thống có tới nhóm ấy là
 * chính Trưởng chi: đọc liên kết trên màn hình rồi đưa tận tay, hoặc nhắn qua Zalo cá nhân.
 *
 * <p><b>Hệ quả phải nói thẳng:</b> Trưởng chi <i>nhìn thấy</i> liên kết, nên trong nửa giờ ấy họ
 * đặt được mật khẩu của người kia. Không có cách sắp xếp nào làm việc đó biến mất chừng nào chưa
 * có kênh SMS/Zalo OA để gửi thẳng cho chính chủ (xem {@code Channel.SMS} — có trong enum, chưa có
 * adapter, và thêm một nhà cung cấp SMS là một hạng mục hạ tầng riêng kèm chi phí thật). Cái làm
 * được hôm nay là khiến việc ấy <b>không im lặng</b>: một dòng {@code password_reset_grant}, một
 * dòng {@code audit_log}, và một thông báo gửi thẳng cho chính chủ.</p>
 *
 * @param url       địa chỉ trao cho người dùng; mang token ký HMAC, hạn ngắn, dùng một lần
 * @param expiresAt hạn của token trong {@link #url}, để màn hình nói được "có hiệu lực tới ..."
 * @param grant     bản ghi tương ứng, <b>không</b> chứa liên kết
 */
public record IssuedPasswordReset(String url, Instant expiresAt, PasswordResetGrantView grant) {
}
