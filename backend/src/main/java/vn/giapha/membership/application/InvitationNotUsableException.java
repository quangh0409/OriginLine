package vn.giapha.membership.application;

import vn.giapha.membership.domain.InvitationUsability;
import vn.giapha.shared.exception.DomainException;

/**
 * Mã khớp một lời mời <b>có thật</b>, nhưng lời mời ấy đã hết hạn / đã dùng / bị thu hồi.
 *
 * <h2>Ba ca hỏng, ba mã lỗi, ba màn hình</h2>
 * Người cầm mã này <b>có</b> một mã thật. "Mã đã hết hạn, gọi Trưởng chi" là thông tin họ cần để đi
 * tiếp; giấu đi chỉ tạo ra một cú điện thoại khó hiểu. Vì client phân nhánh theo {@code code} —
 * quy ước của cả dự án — nên lý do phải nằm ở {@code code}, không ở một thuộc tính mở rộng. Xem
 * javadoc của {@link MembershipProblemCodes#INVITATION_EXPIRED} để biết vì sao bản dựng đầu chọn
 * ngược lại và vì sao lựa chọn ấy sai.
 *
 * <p>Ca thứ tư — mã <b>không khớp</b> lời mời nào — không đi qua lớp này: nó là
 * {@code NotFoundException} với mã {@code NOT_FOUND} sẵn có. Không dựng đối tượng
 * {@code Invitation} nào thì cũng không có {@code usability} nào để hỏi.</p>
 *
 * <h2>Thân lỗi tuyệt đối không mang tên hay số điện thoại người mời</h2>
 * Một mã hỏng, theo định nghĩa, là mã <i>có thể đang nằm trong tay người lạ</i>. Gắn số máy thật
 * của một Trưởng chi vào phản hồi lỗi là biến kẻ dò mã thành kẻ quét danh bạ. Giao diện chỉ dẫn
 * người dùng về số đã nằm sẵn trong tay họ — cuối tin nhắn, đáy phiếu giấy.
 */
public class InvitationNotUsableException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient InvitationUsability usability;

    public InvitationNotUsableException(InvitationUsability usability) {
        super(codeOf(usability), messageOf(usability));
        this.usability = usability;
    }

    public InvitationUsability usability() {
        return usability;
    }

    private static String codeOf(InvitationUsability usability) {
        return switch (usability) {
            case EXPIRED -> MembershipProblemCodes.INVITATION_EXPIRED;
            case ALREADY_USED -> MembershipProblemCodes.INVITATION_ALREADY_USED;
            case REVOKED -> MembershipProblemCodes.INVITATION_REVOKED;
            case USABLE -> MembershipProblemCodes.VALIDATION_FAILED;
        };
    }

    private static String messageOf(InvitationUsability usability) {
        return switch (usability) {
            case EXPIRED -> "Ma moi da het han";
            case ALREADY_USED -> "Ma moi da duoc su dung";
            case REVOKED -> "Ma moi da bi thu hoi";
            case USABLE -> "Ma moi khong dung duoc";
        };
    }
}
