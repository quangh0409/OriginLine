package vn.giapha.demo.model;

import java.util.UUID;

/**
 * Một lớp tên của nhân khẩu (FR-1.2).
 *
 * @param id       khoá chính tất định
 * @param personId chủ nhân
 * @param type     HUY · TU · HIEU · THUY · THUONG_GOI · PHAP_DANH
 * @param fullName tên đầy đủ CÓ dấu tiếng Việt (cột không dấu do DB tự sinh)
 * @param hannom   tên chữ Hán/Nôm, có thể null
 * @param primary  tên hiển thị mặc định — mỗi người đúng một tên primary
 * @param note     ghi chú, ví dụ đánh dấu ca kiểm thử kỵ húy
 */
public record DemoName(UUID id, UUID personId, String type, String fullName,
                       String hannom, boolean primary, String note) {
}
