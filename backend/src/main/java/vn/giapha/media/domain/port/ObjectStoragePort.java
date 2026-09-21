package vn.giapha.media.domain.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Cổng ra kho đối tượng. <b>Đây là lớp duy nhất trong cả cây nguồn được phép nhắc tới SDK MinIO</b>
 * — hiện thực nằm ở {@code media.infrastructure.minio}, và javadoc phụ thuộc trong {@code pom.xml}
 * ghi lại ràng buộc ấy.
 *
 * <h2>Vì sao URL ĐÃ KÝ, không phải đẩy tệp qua backend</h2>
 * Một video 100 MiB đi xuyên qua Tomcat chiếm trọn một luồng xử lý suốt thời gian tải. Với
 * {@code server.tomcat.threads.max} mặc định là 200, chỉ cần vài chục người trong họ cùng đăng ảnh
 * sau một lễ giỗ là hết luồng — và triệu chứng không phải "tải ảnh chậm" mà là <b>cả trang web
 * đứng</b>, kể cả với người chỉ đang xem phả đồ. Thêm nữa, trần {@code spring.servlet.multipart}
 * của dự án đang là 10 MB và nó <b>cố ý</b> khớp với {@code ImportLimits.MAX_FILE_BYTES} (xem khối
 * "BẪY SỐ 3" trong {@code application.yml} và {@code ImportMultipartLimitTest}); nâng nó lên vì
 * video sẽ làm hỏng phép ghim ấy và mở cửa cho một tệp .xlsx 100 MB đi vào đường nhập liệu.
 *
 * <p>Luồng đúng, và nó không có bước nào tin lời client:
 * <ol>
 *   <li>client xin phiếu → backend {@link #presignPut} và ghi một hàng {@code PENDING};</li>
 *   <li>client {@code PUT} thẳng lên MinIO — backend không tham gia, không tốn luồng;</li>
 *   <li>client gọi xác nhận → backend {@link #stat} + {@link #readRange} để <b>tự nhìn thấy</b>
 *       tệp, dò chữ ký byte, đo thời lượng, rồi mới chuyển sang {@code READY}.</li>
 * </ol>
 *
 * <h2>Bucket để PRIVATE, không có lối đọc ẩn danh</h2>
 * {@code infra/docker-compose.yml} đã đặt {@code mc anonymous set none} cho mọi bucket, với đúng
 * lý do: ảnh người còn sống là dữ liệu Tầng 3 theo Nghị định 13/2023. Nên lối đọc duy nhất là
 * {@link #presignGet}, và mỗi lần ký là một lần phép kiểm quyền chạy lại từ đầu.
 */
public interface ObjectStoragePort {

    /** Bucket đang dùng cho ảnh/video của bài viết và ảnh chân dung. */
    String bucket();

    /**
     * URL {@code PUT} đã ký, hạn ngắn.
     *
     * <p><b>Không ký được {@code Content-Length}.</b> Chữ ký truy vấn SigV4 chỉ phủ phương thức,
     * khoá và hạn; ép thêm {@code Content-Length} vào tập header đã ký sẽ làm mọi lượt tải từ
     * trình duyệt hỏng khi con số lệch dù một byte. Vì thế trần dung lượng được giữ ở <b>hai</b>
     * chỗ khác: từ chối phát phiếu khi số byte client khai đã vượt trần (tiết kiệm băng thông cho
     * người trung thực), và {@link #stat} lúc xác nhận (con số này mới là con số có thẩm quyền,
     * và nó bắt người nói dối).</p>
     */
    String presignPut(String objectKey, Duration ttl);

    /** URL {@code GET} đã ký, hạn ngắn. Lối đọc duy nhất — bucket không mở ẩn danh. */
    String presignGet(String objectKey, Duration ttl);

    /**
     * Siêu dữ liệu của đối tượng trên kho.
     *
     * @return rỗng khi kho <b>không có</b> đối tượng nào ở khoá ấy — tức là client gọi xác nhận mà
     *         chưa hề tải gì lên
     */
    Optional<StoredObject> stat(String objectKey);

    /**
     * Đọc một khoảng byte. Dùng để dò chữ ký và đo thời lượng mà không tải cả tệp về.
     *
     * @param offset vị trí bắt đầu; âm nghĩa là đếm ngược từ cuối tệp (hộp {@code moov} của một
     *               MP4 chưa "faststart" nằm ở cuối)
     */
    byte[] readRange(String objectKey, long offset, int length);

    /**
     * Xoá byte khỏi kho. <b>Không</b> xoá hàng {@code media_asset} — hàng ấy ở lại làm sổ với
     * {@code status = PURGED}. Idempotent: xoá một khoá không tồn tại không phải lỗi.
     */
    void delete(String objectKey);

    /** Siêu dữ liệu tối thiểu mà mọi kho S3 đều trả về. */
    record StoredObject(long sizeBytes, String declaredContentType) {
    }
}
