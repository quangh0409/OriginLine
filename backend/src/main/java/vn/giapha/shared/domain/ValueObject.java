package vn.giapha.shared.domain;

/**
 * Đánh dấu một value object: bất biến, không có định danh riêng, so sánh theo giá trị.
 *
 * <p>Ưu tiên hiện thực bằng {@code record}. Interface này chỉ để đọc code cho rõ ý và để
 * ArchUnit/Modulith soi được, không thêm hành vi.</p>
 */
public interface ValueObject {
}
