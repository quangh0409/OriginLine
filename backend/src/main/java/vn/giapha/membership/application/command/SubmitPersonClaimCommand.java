package vn.giapha.membership.application.command;

import java.util.UUID;

/**
 * "Tôi là người này trong phả" — đơn nhận một nhân khẩu <b>đã có</b>.
 *
 * @param personId     ô mình vừa chọn trên phả đồ
 * @param phone        số điện thoại người khai. <b>Bắt buộc</b>: nó là thứ Trưởng chi dùng để gọi
 *                     kiểm chứng, và một đơn không gọi kiểm chứng được thì Trưởng chi không có gì
 *                     để đối chiếu ngoài niềm tin
 * @param introduction vài dòng tự giới thiệu — <i>con ông nào, bà nào, quê quán</i>. Đây mới là thứ
 *                     Trưởng chi thật sự dùng để đối chiếu; số điện thoại chỉ giúp gọi kiểm chứng
 *                     (design 07 §1.3)
 */
public record SubmitPersonClaimCommand(UUID personId, String phone, String introduction) {
}
