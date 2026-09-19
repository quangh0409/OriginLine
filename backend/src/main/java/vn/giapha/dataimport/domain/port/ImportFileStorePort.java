package vn.giapha.dataimport.domain.port;

import java.util.Optional;

/**
 * Cổng lưu <b>tệp gốc</b> người dùng gửi lên.
 *
 * <h2>Rẻ bây giờ, đắt gấp bội khi cần mà không có</h2>
 * Giá: vài MB mỗi lô. Giá trị: đây là bằng chứng duy nhất về việc Trưởng chi <b>thật sự đã gõ
 * gì</b>. Ba tháng sau, khi một cụ cao niên chỉ vào phả đồ và nói sai rồi, tệp gốc là thứ phân
 * biệt được hai chuyện có cách chữa hoàn toàn khác nhau: họ chép sai sổ, hay ta phân tích sai tệp.
 * Không có nó thì mọi cuộc truy nguyên đều dừng ở phỏng đoán.
 *
 * <p>Tệp đi vào kho đối tượng (MinIO, bucket riêng), <b>không</b> vào cơ sở dữ liệu — ràng buộc
 * kiến trúc chung: không bao giờ lưu blob trong CSDL.</p>
 */
public interface ImportFileStorePort {

    /**
     * @return khoá đối tượng, hoặc rỗng khi kho đối tượng chưa được nối vào
     */
    Optional<String> store(byte[] content, String filename, String contentSha256);
}
