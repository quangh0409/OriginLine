package vn.giapha.notification.domain;

import java.util.UUID;

/**
 * Một dòng hộp thư <b>kèm hai giá trị suy ra</b> mà bảng {@code notification_inbox} không có cột.
 *
 * <p>Hợp đồng OpenAPI yêu cầu {@code personId} (nhân khẩu được giỗ, để giao diện điều hướng) và
 * {@code reminderOffsetDays} (mốc 7/3/1 đã bắn). Cả hai nằm ở {@code event.person_id} và
 * {@code reminder_job.offset_days}, lấy được bằng hai phép {@code LEFT JOIN} ngay trong truy vấn
 * danh sách.</p>
 *
 * <p>Chọn join lúc đọc thay vì nhân bản thành cột lúc ghi vì migration thuộc sở hữu của W1 và không
 * được sửa từ W5 — và vì dữ liệu nhân bản rồi sẽ lệch khi ai đó sửa sự kiện. Cái giá là một phép
 * join trên khoá chính, rẻ hơn nhiều so với một trường sai.</p>
 */
public record InboxEntry(InboxItem item, UUID subjectPersonId, Integer reminderOffsetDays) {
}
