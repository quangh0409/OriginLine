package vn.giapha.membership.application;

/**
 * Kết quả của lệnh phát mã dòng họ — <b>lần duy nhất mã thô tồn tại ngoài tay người dùng</b>.
 *
 * <p>CSDL chỉ lưu băm, và không endpoint nào đọc lại được mã. Màn hình phát mã phải cho Hội đồng
 * chép hoặc in ngay, và nói rõ rằng đóng màn là mất mã. Mất mã thì <b>phát mã mới</b> — và mã cũ ở
 * lại với bộ đếm của nó, vì chính con số ấy là thứ đáng giữ.</p>
 *
 * <p>Khác luồng mã cá nhân ở một điểm: phát mã dòng họ mới <b>không</b> tự thu hồi mã cũ. Mã cá
 * nhân phải thu hồi vì hai mã cùng mở được <i>một hồ sơ</i> thì "thu hồi" mất nghĩa. Mã dòng họ
 * không mở hồ sơ nào, và nhiều mã song song là chuyện bình thường — mỗi kênh phát một mã chính là
 * cách Hội đồng biết mã nào đã rò.</p>
 *
 * @param code   mã thô đã chia nhóm cho dễ đọc qua điện thoại ({@code K7M2Q-D9HFX})
 * @param invite bản ghi mã, <b>không</b> chứa mã lẫn băm
 */
public record IssuedClanInvite(String code, ClanInviteView invite) {
}
