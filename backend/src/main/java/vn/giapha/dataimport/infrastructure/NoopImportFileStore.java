package vn.giapha.dataimport.infrastructure;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.port.ImportFileStorePort;

/**
 * Hiện thực tạm của {@link ImportFileStorePort}: <b>không lưu gì cả</b>, và nói ra điều đó.
 *
 * <h2>Vì sao đợt này chưa lưu tệp gốc</h2>
 * Hạ tầng đã có MinIO và bốn bucket, nhưng <b>backend chưa có SDK S3/MinIO nào</b> — tìm
 * {@code MinioClient}, {@code S3Client}, {@code software.amazon} trên cả cây nguồn trả 0 kết quả.
 * Thêm phụ thuộc ấy là một hạng mục riêng (cộng một bucket mới cho tệp nhập liệu), không thuộc
 * phạm vi nửa đầu đường ống.
 *
 * <h2>Cái giá của việc thiếu nó, nói thẳng</h2>
 * Ba tháng sau, khi một cụ cao niên chỉ vào phả đồ và nói sai rồi, tệp gốc là thứ duy nhất phân
 * biệt được hai chuyện có cách chữa hoàn toàn khác nhau: <b>họ chép sai sổ</b>, hay <b>ta phân
 * tích sai tệp</b>. Không có nó thì mọi cuộc truy nguyên dừng ở phỏng đoán, và cả hai bên đều tin
 * là mình đúng.
 *
 * <p>Vì vậy lớp này <b>ghi WARN mỗi lần</b> thay vì im lặng trả rỗng: một hiện thực giả im lặng là
 * cách tốt nhất để món nợ này bị quên mất cho tới lúc cần.</p>
 */
@Component
public class NoopImportFileStore implements ImportFileStorePort {

    private static final Logger log = LoggerFactory.getLogger(NoopImportFileStore.class);

    @Override
    public Optional<String> store(byte[] content, String filename, String contentSha256) {
        log.warn("Chua noi kho doi tuong: tep goc {} ({} byte, sha256={}) KHONG duoc luu lai."
                        + " Truy nguyen ve sau se khong co ban goc de doi chieu.",
                filename, content == null ? 0 : content.length, contentSha256);
        return Optional.empty();
    }
}
