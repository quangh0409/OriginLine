package vn.giapha.media.infrastructure.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình kho đối tượng. Tiền tố {@code giapha.media}.
 *
 * <h2>Khoá thật đến từ BIẾN MÔI TRƯỜNG, không từ tệp trong repo</h2>
 * {@code application.yml} chỉ khai các giá trị mặc định của <b>máy phát triển</b>
 * ({@code ${MINIO_ACCESS_KEY:giapha}}), đúng như mọi thông tin đăng nhập dev khác của dự án —
 * chúng nằm công khai trong {@code infra/docker-compose.yml} và {@code README.md} vì chúng chỉ mở
 * được một MinIO chạy trên máy của chính người phát triển. Ở môi trường thật, hai biến
 * {@code MINIO_ACCESS_KEY} / {@code MINIO_SECRET_KEY} <b>phải</b> đến từ môi trường.
 *
 * <h2>{@code publicEndpoint} — cái bẫy đắt nhất của URL đã ký</h2>
 * Chữ ký SigV4 phủ cả <b>tên máy chủ</b>. Trong Docker Compose, backend nói chuyện với MinIO qua
 * {@code http://minio:9000} (tên dịch vụ trong mạng nội bộ), nhưng trình duyệt của người dùng
 * <i>không phân giải được</i> tên ấy. Ký bằng một tên rồi giao cho trình duyệt dùng một tên khác
 * thì MinIO trả {@code SignatureDoesNotMatch} — một thông báo nói về chữ ký, trong khi lỗi thật
 * nằm ở tên máy chủ. Vì vậy có <b>hai</b> địa chỉ:
 * <ul>
 *   <li>{@link #getEndpoint()} — backend dùng để gọi API ({@code statObject}, đọc khoảng, xoá);</li>
 *   <li>{@link #getPublicEndpoint()} — địa chỉ <b>ký vào URL</b>, tức địa chỉ mà trình duyệt sẽ
 *       gọi. Để trống thì dùng lại {@code endpoint}, đúng cho máy phát triển nơi cả hai là
 *       {@code localhost:9000}.</li>
 * </ul>
 */
// @Component chu khong @EnableConfigurationProperties o mot lop @Configuration: dung khuon
// ma ReminderProperties da lap, nen khong co mot lop cau hinh rong chi de dang ky mot bean.
@Component
@ConfigurationProperties(prefix = "giapha.media")
public class MinioProperties {

    /** Địa chỉ backend dùng để gọi API kho. */
    private String endpoint = "http://localhost:9000";

    /** Địa chỉ ký vào URL — địa chỉ mà trình duyệt gọi. Trống = dùng {@link #endpoint}. */
    private String publicEndpoint = "";

    private String accessKey = "giapha";

    private String secretKey = "giapha123";

    /**
     * Bucket cho ảnh/video của bài viết và ảnh chân dung.
     *
     * <p>Tách khỏi bốn bucket đã có trong {@code docker-compose}
     * ({@code giapha-portraits}/{@code stele}/{@code graves}/{@code documents}) vì ba bucket sau
     * là <b>của giai đoạn 3</b> (heritage: bia, mộ, tài liệu scan) và sẽ có chính sách vòng đời
     * khác hẳn — tài liệu scan thì giữ vĩnh viễn, còn tệp của bài viết thì đường dọn được phép xoá.
     * Trộn chúng nghĩa là mỗi chính sách phải tự lọc theo tiền tố, và một chính sách lọc sai ở kho
     * là một lần mất dữ liệu không hoàn tác được.</p>
     */
    private String bucket = "giapha-media";

    /**
     * Tự tạo bucket lúc khởi động nếu chưa có.
     *
     * <p>Bật ở máy phát triển và trong bài kiểm tích hợp; ở môi trường thật nên <b>tắt</b> và để
     * người vận hành tạo bucket kèm đúng chính sách (không đọc ẩn danh, versioning, vòng đời). Một
     * bucket do ứng dụng tự tạo sẽ mang chính sách mặc định của máy chủ, và mặc định của máy chủ
     * không phải thứ ta muốn cho ảnh người còn sống.</p>
     */
    private boolean autoCreateBucket = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    /** Hai địa chỉ có khác nhau không — dùng để quyết định có cần một client ký riêng hay không. */
    public boolean hasSeparatePublicEndpoint() {
        return !getPublicEndpoint().equals(endpoint);
    }
}
