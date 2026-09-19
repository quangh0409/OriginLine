package vn.giapha.membership.domain;

/**
 * Lời mời có dùng được <b>ngay lúc này</b> không, và nếu không thì vì sao.
 *
 * <h2>Vì sao tách khỏi {@link InvitationStatus}</h2>
 * {@code InvitationStatus} là thứ nằm trong CSDL; kiểu này là thứ trả lời câu hỏi của người đang
 * cầm mã, và câu trả lời ấy phụ thuộc vào đồng hồ. Trộn hai khái niệm lại thì "hết hạn" buộc phải
 * trở thành một dòng dữ liệu cần job đi lật — xem javadoc của {@code InvitationStatus}.
 *
 * <h2>Ba lý do thất bại được phân biệt, lý do thứ tư thì không</h2>
 * {@link #EXPIRED}, {@link #ALREADY_USED}, {@link #REVOKED} được nói thẳng cho người gọi: họ đang
 * cầm một mã có thật, và "mã đã hết hạn, gọi ông Bốn" là thông tin họ cần để đi tiếp. Nhưng
 * <b>mã không tồn tại</b> không có mặt trong enum này — nó không bao giờ dựng được một
 * {@code Invitation} nên không có gì để hỏi, và ở tầng trên nó cũng trả về cùng một mã lỗi chung.
 * Phân biệt "mã sai" với "mã hết hạn" sẽ biến endpoint tra mã thành máy xác nhận mã tồn tại.
 */
public enum InvitationUsability {

    USABLE,

    /** Quá {@code expires_at}. Hạn ngắn là một trong ba lớp chống đỡ, không phải chi tiết vận hành. */
    EXPIRED,

    /** Đã có người nhận. Mã mời là bí mật <b>một lần</b>: dùng xong thì chết. */
    ALREADY_USED,

    /** Bị Trưởng chi thu hồi, hoặc bị chính người nhận bấm "Không phải tôi". */
    REVOKED;

    public boolean isUsable() {
        return this == USABLE;
    }
}
