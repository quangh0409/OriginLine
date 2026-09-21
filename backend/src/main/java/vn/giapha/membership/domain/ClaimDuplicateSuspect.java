package vn.giapha.membership.domain;

import java.util.List;
import java.util.UUID;

/**
 * Một nhân khẩu <b>đã có trong phả</b> bị nghi là chính người đang khai "tôi chưa có trong phả".
 *
 * <h2>Vì sao kiểu này tồn tại ở phía membership</h2>
 * Ràng buộc 3 của design 07 §1.5: đơn phải chạy qua bộ dò trùng <b>trước khi</b> tới tay Trưởng
 * chi. Người khai rất có thể <i>đã</i> có trong phả dưới một tên khác — tên huý, tên thường gọi —
 * và đây đúng là kịch bản bộ dò sinh ra để phục vụ. Trưởng chi phải thấy ngay "có thể đây là người
 * này" thay vì tạo ra một bản trùng, mà gộp nhầm hai người trong gia phả là loại lỗi rất khó gỡ:
 * cả hai nhánh con cháu đều đã treo vào node sai.
 *
 * <h2>Ba trường, và ba trường bị bỏ đi có chủ ý</h2>
 * Bộ dò bên {@code genealogy} trả về cả {@code displayName}, {@code generation}, {@code branchId}
 * và {@code matchedName} của bên bị nghi. Kiểu này <b>không</b> chở chúng, vì javadoc của
 * {@code DuplicateMatch} đã chốt ranh giới cho mọi kênh đi ra khỏi tiến trình: <i>được phép nói
 * trường nào của chính người gọi vừa nhập đã khớp, không bao giờ nói một giá trị đọc từ phả</i>.
 * Bên bị nghi hoàn toàn có thể là một người <b>còn sống ở một chi khác</b> mà người đọc không được
 * xem tên huý hay năm sinh. Giao diện cầm {@link #personId()} rồi gọi
 * {@code GET /api/v1/persons/&#123;id&#125;}, nơi bộ lọc phân tầng riêng tư thật sự chạy.
 *
 * <p>Điều đó đặc biệt quan trọng ở đây vì bản ghi này được <b>lưu</b> vào
 * {@code person_claim.screening}: một giá trị đọc từ phả lọt vào JSONB ấy là lọt vĩnh viễn, và nó
 * sẽ đi qua mọi lần đọc đơn về sau mà không còn bộ lọc nào chạy trên nó.</p>
 *
 * @param personId nhân khẩu bị nghi — khoá tra cứu, và là thứ duy nhất giao diện cần
 * @param score    điểm nghi ngờ đã cộng trừ mọi tín hiệu; cao hơn nghĩa là đáng xem hơn
 * @param signals  tên các tín hiệu đã đóng góp, dạng chuỗi — <b>chỉ để hiển thị</b>
 * @param hint     câu giải thích ngắn cho người đọc — <b>chỉ để hiển thị</b>, đừng phân tích chuỗi
 */
public record ClaimDuplicateSuspect(UUID personId, int score, List<String> signals, String hint) {

    public ClaimDuplicateSuspect {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
