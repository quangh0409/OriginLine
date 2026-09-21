package vn.giapha.membership.application;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.port.SetPasswordLink;

/**
 * Kết quả của lệnh đăng ký bằng mã mời dòng họ.
 *
 * <h2>Tài khoản ra khỏi đây ở trạng thái {@code PENDING} và KHÔNG có {@code personId}</h2>
 * Đây là điểm phân biệt căn bản với {@link AcceptedInvitation}. Mã cá nhân mang sẵn nhân khẩu nên
 * người nhận thành {@code ACTIVE} ngay; mã dòng họ không trỏ vào ai, nên người đăng ký mới chỉ có
 * một tài khoản. Họ <b>xem được phả đồ ngay</b> (quyết định đã chốt ở design 07 §1.1: phải nhìn
 * thấy phả mới tự nhận mình được), rồi gửi đơn tự nhận và chờ Trưởng chi duyệt.
 *
 * <p>Trạng thái "có tài khoản, chưa gắn vào phả" <b>không phải thứ mới</b>: nó đã có sẵn trong dữ
 * liệu từ V5 ({@code app_user.person_id} rỗng, {@code status = PENDING}).</p>
 *
 * @param user            tài khoản vừa lập hoặc vừa tìm thấy
 * @param setPasswordLink liên kết một lần để chính họ đặt mật khẩu; <b>rỗng</b> khi tài khoản đã
 *                        có mật khẩu — phát liên kết cho họ là mở một lối đổi mật khẩu cho bất kỳ
 *                        ai cầm mã dòng họ, mà mã ấy thì cả họ đang cầm
 * @param clanInviteId    mã đã dùng, để màn hình nói được "bạn vào bằng mã nào" và để nhật ký nối
 *                        được hai đầu
 */
public record ClanRegistration(AppUser user, Optional<SetPasswordLink> setPasswordLink,
                               UUID clanInviteId) {
}
