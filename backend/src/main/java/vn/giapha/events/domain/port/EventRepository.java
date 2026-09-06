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
     * Sự kiện còn hiệu lực và lặp lại hằng năm — đầu vào của scheduler sinh lịch nhắc.
     *
     * <p>Nạp theo lô để một dòng họ lớn không kéo cả bảng vào bộ nhớ mỗi đêm. Phân trang theo
     * offset (không phải keyset) là đủ: bảng {@code event} ở quy mô một dòng họ là hàng trăm tới
     * hàng nghìn dòng, và job chạy đêm nên không cạnh tranh với ai.</p>
     *
     * @param page trang, bắt đầu từ 0
     * @param size kích thước lô
     */
    List<Event> findRecurringPage(int page, int size);

    /**
     * Bộ lọc của {@code GET /api/v1/events}. Lọc theo khoảng ngày dương và phân trang làm ở tầng
     * application vì "ngày dương của lần sắp tới" là <b>giá trị tính ra</b>, không có trong bảng —
     * không thể đẩy xuống mệnh đề {@code WHERE}.
     *
     * <p><b>Không có phương thức ghi.</b> W5 chỉ đọc bảng {@code event}; màn hình tạo/sửa sự kiện
     * thuộc phạm vi sau. Khai báo sẵn một {@code save()} không ai gọi chỉ tạo ảo giác là đã có luồng
     * ghi kèm audit — mà audit thì chưa có.</p>
     *
     * @param branchId lọc theo chi/ngành, <b>bao gồm nhánh con</b>; sự kiện cấp dòng họ luôn có mặt
     */
    List<Event> search(List<EventType> types, UUID personId, UUID branchId, boolean includeDeleted);
}
