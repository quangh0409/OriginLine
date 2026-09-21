package vn.giapha.media.infrastructure.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tạo bucket lúc khởi động nếu nó chưa có.
 *
 * <h2>Vì sao ở đây chứ không ở {@code docker-compose.yml}</h2>
 * {@code infra/docker-compose.yml} <b>đã</b> tạo sẵn bốn bucket, nhưng chúng thuộc làn của một
 * agent khác ở đợt này và — quan trọng hơn — chúng là bucket của giai đoạn 3 (bia, mộ, tài liệu
 * scan) với chính sách vòng đời khác hẳn. Thêm nữa, một bucket chỉ tồn tại nhờ một dòng trong tệp
 * compose sẽ <b>không</b> tồn tại trong bài kiểm tích hợp chạy Testcontainers, và triệu chứng là
 * {@code NoSuchBucket} ở lần gọi đầu — một lỗi trông như lỗi mã.
 *
 * <h2>Chạy lúc {@code ApplicationReadyEvent}, KHÔNG lúc tạo bean</h2>
 * Cùng bài học với "BẪY SỐ 2" của {@code application.yml} (Keycloak): một bean gọi ra hạ tầng
 * ngoài <i>trong lúc khởi tạo</i> làm cả ứng dụng chết khi hạ tầng ấy chưa sẵn sàng, và
 * {@code /actuator/health} không bao giờ xanh. Ở đây còn thêm một lý do: kho tệp <b>không phải</b>
 * phụ thuộc bắt buộc để xem phả đồ. MinIO sập thì tính năng ảnh hỏng, chứ cả hệ thống gia phả
 * không được sập theo. Vì vậy lỗi ở đây chỉ ghi {@code ERROR} rồi đi tiếp.
 *
 * <p><b>Không</b> đặt chính sách bucket ở đây. Mặc định của MinIO là riêng tư, và đó là thứ ta
 * muốn; viết thêm một chính sách bằng mã là mở đường cho một lần gõ nhầm biến bucket ảnh người
 * còn sống thành công khai. Ở môi trường thật, đặt {@code giapha.media.auto-create-bucket: false}
 * và để người vận hành tạo bucket kèm đúng chính sách.</p>
 */
@Component
public class MinioBucketInitializer {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioProperties props;

    public MinioBucketInitializer(MinioProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        if (!props.isAutoCreateBucket()) {
            log.info("Bo qua viec tu tao bucket {} (auto-create-bucket = false)", props.getBucket());
            return;
        }
        try (MinioClient client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build()) {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (exists) {
                log.debug("Bucket {} da co", props.getBucket());
                return;
            }
            client.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
            log.info("Da tao bucket {} (rieng tu theo mac dinh cua may chu)", props.getBucket());
        } catch (Exception ex) {
            log.error("Khong kiem/tao duoc bucket {} tren {}. Tinh nang anh/video se khong chay,"
                            + " nhung phan con lai cua he thong van hoat dong binh thuong.",
                    props.getBucket(), props.getEndpoint(), ex);
        }
    }
}
