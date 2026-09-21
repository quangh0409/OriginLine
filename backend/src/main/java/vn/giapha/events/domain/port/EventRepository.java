package vn.giapha.events.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventType;

/**
 * Cổng đọc/ghi sự kiện. Hiện thực nằm ở {@code events.infrastructure.jpa}.
 */
public interface EventRepository {

    Optional<Event> findById(UUID id);

    /**
     * Sự kiện <b>còn hiệu lực</b> (chưa xoá mềm) — đầu vào của scheduler sinh lịch nhắc.
     *
     * <p>Trước lối ghi thủ công, phương thức này lọc thêm {@code is_recurring = TRUE}, vì khi ấy
     * bảng chỉ chứa giỗ. Điều kiện đó nay là một lỗ hổng: một buổi họp họ Chủ nhật tới không lặp
     * lại năm nào và là sự kiện <b>cần được nhắc nhất</b> trong cả bảng. Việc phân biệt "lặp hay
     * một lần" đã chuyển vào {@code OccurrenceResolver.resolveAll}, nơi nó thuộc về.</p>
     *
     * <p>Nạp theo lô để một dòng họ lớn không kéo cả bảng vào bộ nhớ mỗi đêm. Phân trang theo
     * offset (không phải keyset) là đủ: bảng {@code event} ở quy mô một dòng họ là hàng trăm tới
     * hàng nghìn dòng, và job chạy đêm nên không cạnh tranh với ai.</p>
     *
     * @param page trang, bắt đầu từ 0
     * @param size kích thước lô
     */
    List<Event> findActivePage(int page, int size);

    /**
     * Bộ lọc của {@code GET /api/v1/events}. Lọc theo khoảng ngày dương và phân trang làm ở tầng
     * application vì "ngày dương của lần sắp tới" là <b>giá trị tính ra</b>, không có trong bảng —
     * không thể đẩy xuống mệnh đề {@code WHERE}.
     *
     * @param branchId lọc theo chi/ngành, <b>bao gồm nhánh con</b>; sự kiện cấp dòng họ luôn có mặt
     */
    List<Event> search(List<EventType> types, UUID personId, UUID branchId, boolean includeDeleted);

    /**
     * Ghi một sự kiện mới.
     *
     * @return bản ghi sau khi ghi, kèm {@code version} do cơ sở dữ liệu cấp
     */
    Event insert(Event event);

    /**
     * Ghi đè một sự kiện đã có, <b>có kiểm khoá lạc quan</b>.
     *
     * <p>{@code expectedVersion} là giá trị người gọi đọc được ở lần {@code GET} gần nhất (đi qua
     * {@code ETag}/{@code If-Match}). Lệch nghĩa là có người khác vừa sửa — ném
     * {@code ObjectOptimisticLockingFailureException}, ánh xạ sang <b>409</b>. Gia phả là dữ liệu
     * nhiều người cùng biên tập; ghi mù ở đây là xoá công của người vừa sửa xong mà không ai biết.</p>
     *
     * <p><b>Không có phép xoá cứng, và sẽ không bao giờ có.</b> Xoá mềm đi qua chính phương thức
     * này với {@code deleted = true}: bản ghi ở lại để lịch nhắc đã phát, nhật ký gửi và nhật ký
     * kiểm toán còn chỗ trỏ về.</p>
     */
    Event update(Event event, long expectedVersion);
}
