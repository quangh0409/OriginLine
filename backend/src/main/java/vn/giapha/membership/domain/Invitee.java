package vn.giapha.membership.domain;

import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Nhân khẩu nhìn từ phía luồng mời — <b>tập trường hẹp nhất đủ để người nhận nói "đúng là tôi"</b>.
 *
 * <h2>Đây là một đánh đổi riêng tư có chủ ý, không phải một sơ suất</h2>
 * {@link #displayName()} là dữ liệu Tầng 1 của một người <i>đang sống</i>, và màn nhận lời mời hiện
 * nó ra cho bất kỳ ai cầm mã — kể cả người nhặt được tờ phiếu, kể cả người được chuyển tiếp tin
 * nhắn. BA v2 §10 nói người đang sống ẩn mặc định, nên đây là một ngoại lệ, và nó được chọn có ý
 * thức (design 06 §5.3 phương án (a)): giấu tên thì nút "Không phải tôi" mất nghĩa, cụ nhận tin
 * không biết lời mời có dành cho mình không, và nghi ngờ là phản ứng đúng — nên cụ sẽ không bấm,
 * và cả luồng vô nghĩa.
 *
 * <p>Cái giá được trả bằng ba lớp chống đỡ, cả ba đều bắt buộc và không lớp nào thay được lớp nào:
 * mã <b>dùng một lần</b> · <b>hạn ngắn</b> · <b>giới hạn tần suất</b>. Bỏ bất kỳ lớp nào thì mã mời
 * trở thành một khoá mở tên người còn sống, dùng được vô hạn.</p>
 *
 * <h2>Chi/ngành thì được, nhân khẩu thì không</h2>
 * Khối chi ({@link #branchId()}, {@link #branchName()}, {@link #branchPath()}) đi ra tới người chưa
 * đăng nhập, và điều đó <b>không</b> mở thêm gì: {@code PublicPersonDto} của cổng công khai vốn đã
 * chở nguyên một {@code BranchRef} cho Khách. Ngược lại, khoá nhân khẩu <b>không</b> được đưa ra
 * màn nhận lời mời — nó là khoá tra cứu ở mọi endpoint khác, tức một khoá nối bền vững trao cho một
 * người chưa xác thực, trong khi màn hình chẳng dùng tới nó. Nó chỉ xuất hiện ở phản hồi của lệnh
 * <i>nhận</i>, khi người gọi đã có token.
 *
 * <p>Vì lẽ đó kiểu này <b>không</b> mang năm sinh, nghề nghiệp, nơi ở, điện thoại hay ảnh. Thêm một
 * trường vào đây là mở rộng đúng thứ mà một mã rò rỉ sẽ tiết lộ — hãy nêu được lý do trước khi thêm.</p>
 *
 * @param personId     khoá nhân khẩu; dùng nội bộ, <b>không</b> ra tới màn nhận lời mời
 * @param displayName  tên chính (ưu tiên tên hiển thị mặc định, sau đó tên huý)
 * @param generation   đời thứ; {@code null} khi chưa ghi
 * @param branchId     khoá chi/ngành
 * @param branchName   tên chi/ngành có dấu, để người nhận nhận ra "họ nhà mình"
 * @param branchPath   {@code ltree} của chi — vừa dùng cho kiểm phạm vi, vừa là {@code path} của
 *                     {@code BranchRef}
 * @param branchRegion vùng miền của chi ({@code BAC}/{@code TRUNG}/{@code NAM}), có thể null
 * @param clanName     tên gốc của cả dòng họ, lấy từ nhãn cấp 1 của {@code ltree}
 * @param alive        còn sống hay không
 * @param deleted      đã xoá mềm hay chưa
 */
public record Invitee(UUID personId, String displayName, Integer generation,
                      UUID branchId, String branchName, BranchPath branchPath, String branchRegion,
                      String clanName, boolean alive, boolean deleted) {
}
