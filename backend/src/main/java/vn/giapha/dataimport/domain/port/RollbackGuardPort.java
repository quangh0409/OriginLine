package vn.giapha.dataimport.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng trả lời đúng một câu hỏi, và là câu hỏi quyết định của việc gỡ lô:
 * <b>đã có ai khác động vào những người của lô này chưa?</b>
 *
 * <h2>Vì sao thời hạn không phải điều kiện thật</h2>
 * Cửa sổ 7 ngày chỉ là cái chặn cuối. Điều kiện thật là phần dưới đây: một lô hai ngày tuổi mà đã
 * có người treo thêm một đứa con vào thì gỡ nó sẽ làm đứa bé mất cha trong phả đồ, còn một lô sáu
 * ngày tuổi chưa ai đụng tới thì gỡ sạch sẽ. Lấy thời hạn làm điều kiện chính là cách sinh ra một
 * nút hoàn tác <b>làm mất dữ liệu</b> — tệ hơn hẳn không có nút nào, vì nó tạo cảm giác an toàn
 * giả.
 *
 * <p>Mỗi phương thức trả về một <b>câu tiếng Việt</b> mô tả vướng mắc, chứ không phải một
 * {@code boolean}. Người dùng bị từ chối còn phải quyết định làm tay, nên họ cần biết vướng ở đâu
 * — "không thể hoàn tác" là một câu vô dụng.</p>
 */
public interface RollbackGuardPort {

    /**
     * Những người của lô đã bị <b>sửa hồ sơ</b> kể từ lúc ghi.
     *
     * <p>So {@code person.version} hiện tại với giá trị đã chụp trong sổ cái. Không dùng
     * {@code updated_at}: hai lệnh ghi trong cùng một mili-giây là chuyện có thật, còn version thì
     * không nói dối.</p>
     *
     * @return mô tả từng người đã bị sửa; rỗng nghĩa là chưa ai đụng tới
     */
    List<String> hoSoDaBiSua(UUID batchId);

    /**
     * Cạnh quan hệ <b>mới</b> chạm vào người của lô mà không do lô này nối.
     *
     * <p>Đây là ca nguy hiểm nhất: ai đó đã treo thêm một đứa con, hoặc nối một người vợ, vào một
     * người mà lô vừa tạo. Gỡ lô sẽ xoá mềm người cha và để lại đứa con treo lơ lửng.</p>
     */
    List<String> canhMoiTreoVaoLo(UUID batchId, Instant committedAt);

    /**
     * Dữ liệu phụ thuộc mới ngoài phả hệ: tài khoản đã nhận hồ sơ, sự kiện giỗ đã lên lịch, yêu
     * cầu đính chính đang treo, chức danh Trưởng chi đã trỏ tới.
     *
     * <p>Bước 5 của quy trình là mời người còn sống tự nhận hồ sơ qua Zalo. Một khi
     * {@code app_user.person_id} đã trỏ tới một người của lô thì gỡ lô là cắt tài khoản của một
     * người thật khỏi cây — phải liên hệ từng người để sửa, tức là việc tay.</p>
     */
    List<String> phuThuocNgoaiPhaHe(UUID batchId);
}
