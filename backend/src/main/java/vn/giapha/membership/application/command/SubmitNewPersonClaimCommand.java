package vn.giapha.membership.application.command;

import java.util.UUID;
import vn.giapha.membership.domain.RelativeKind;
import vn.giapha.shared.vo.Gender;

/**
 * "Tôi chưa có trong phả" — đơn xin được <b>thêm vào</b> gia phả.
 *
 * <h2>Ba tình huống có thật mà lối này giải (design 07 §1.5)</h2>
 * <ul>
 *   <li><b>Con dâu mới về</b> — chị ấy là người trong họ, nhưng hôn phối mới diễn ra tháng trước và
 *       chưa ai kịp ghi chị vào phả;</li>
 *   <li><b>Cháu mới sinh</b> — bố mẹ có trong phả, đứa bé thì chưa;</li>
 *   <li><b>Người ở xa nhiều đời</b> — một nhánh ra nước ngoài từ lâu, phả giấy của chi không còn
 *       ghi tiếp.</li>
 * </ul>
 * Cả ba cầm mã mời hợp lệ, đăng ký thành công, mở phả đồ ra và <b>không tìm thấy mình</b>. Không có
 * lối đi này thì họ sẽ <i>chọn bừa một người gần đúng</i> — thường là người cùng tên, hoặc bố mình
 * — rồi Trưởng chi nhận một đơn vô nghĩa và không hiểu vì sao. Việc ấy lặp lại với mọi đám cưới và
 * mọi đứa trẻ mới sinh, tức là <b>vĩnh viễn</b>.
 *
 * @param fullName         họ tên tự khai
 * @param birthYear        năm sinh; không bắt buộc, nhưng nó là tín hiệu tốt của bộ dò trùng
 * @param gender           giới tính; cần để dựng được node, và quyết định vế nào của cạnh hôn phối
 * @param relativePersonId <b>người thân đã có trong phả</b>. Bắt buộc, và đây là ràng buộc 2 của
 *                         §1.5: không có nó thì nhân khẩu mới thành node mồ côi — không gắn vào
 *                         cây, không tính được đời, không tra được danh xưng. Người thân ấy cũng là
 *                         thứ quyết định <b>ai duyệt</b>, vì người mới chưa thuộc chi nào
 * @param relativeKind     bố, mẹ, hay vợ/chồng
 * @param phone            số điện thoại người khai, để Trưởng chi gọi kiểm chứng
 * @param introduction     vài dòng tự giới thiệu — thứ Trưởng chi thật sự dùng để đối chiếu
 */
public record SubmitNewPersonClaimCommand(String fullName, Integer birthYear, Gender gender,
                                          UUID relativePersonId, RelativeKind relativeKind,
                                          String phone, String introduction) {
}
