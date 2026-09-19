package vn.giapha.membership.application;

/**
 * Kết quả của lệnh phát mã — <b>lần duy nhất mã thô tồn tại ngoài tờ phiếu giấy</b>.
 *
 * <h2>Đọc kỹ trước khi dùng kiểu này ở bất cứ đâu khác</h2>
 * {@link #code()} không đọc lại được. Nó không nằm trong CSDL (chỉ có băm), không được ghi vào
 * {@code audit_log}, và không được đưa vào một câu log nào — kể cả ở mức {@code DEBUG}, vì log
 * {@code DEBUG} của môi trường thật vẫn là một tệp nằm trên đĩa.
 *
 * <p>Trưởng chi mất mã thì <b>phát lại</b>; thao tác phát lại tự thu hồi mã cũ. Đó không phải hạn
 * chế của hiện thực mà là chính điều kiện khiến "lưu băm chứ không lưu mã thô" có nghĩa.</p>
 *
 * @param code       mã đã chia nhóm cho dễ đọc, ví dụ {@code K7M2Q-D9HFX}
 * @param invitation bản ghi tương ứng, không chứa mã
 */
public record IssuedInvitation(String code, InvitationView invitation) {
}
