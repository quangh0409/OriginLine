package vn.giapha.genealogy.domain.port;

import java.util.Optional;

/**
 * Cache <b>khung xương</b> của projection cây (danh sách id đỉnh + độ sâu), không phải hồ sơ.
 *
 * <h2>Vì sao chỉ cache khung xương</h2>
 * Phần đắt của {@code GET /tree} là phép duyệt Cypher; phần rẻ là nạp hồ sơ theo khoá chính.
 * Quan trọng hơn: hồ sơ trả về <b>khác nhau theo người gọi</b> (phân tầng riêng tư). Cache thứ đã
 * lọc thì sớm muộn cũng có người nhận được bản cache của một vai khác — một lỗi rò rỉ dữ liệu
 * không để lại dấu vết nào trong log. Khung xương không chứa dữ liệu cá nhân nên dùng chung được
 * cho mọi vai, còn việc lọc thì luôn chạy tươi.
 *
 * <p>Cache <b>bắt buộc</b> bị vô hiệu hoá khi có mutation, nếu không cây sẽ tiếp tục hiển thị
 * người vừa bị xoá mềm.</p>
 */
public interface TreeCachePort {

    Optional<String> get(String key);

    void put(String key, String value);

    /**
     * Xoá toàn bộ vùng cache cây. Cố ý thô: xác định chính xác những gốc nào chứa một nhân khẩu
     * vừa đổi lại cần chính phép duyệt mà ta đang muốn tránh. Cây đúng quan trọng hơn cache ấm.
     */
    void evictAll();
}
