package vn.giapha.media.infrastructure.minio;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.media.domain.port.ObjectStoragePort;

/**
 * Hiện thực {@link ObjectStoragePort} trên SDK MinIO.
 *
 * <p><b>Đây là lớp DUY NHẤT trong cả cây nguồn được phép {@code import io.minio}.</b> Ràng buộc ấy
 * cũng được ghi ngay trên khối khai báo phụ thuộc trong {@code pom.xml} — đó là chỗ người ta nhìn
 * khi định dùng SDK ở một nơi thứ hai. Đổi sang S3 thật, hay sang một nhà cung cấp khác, là sửa
 * một tệp.</p>
 *
 * <h2>Hai client, và vì sao</h2>
 * {@code apiClient} trỏ vào địa chỉ backend gọi được; {@code signingClient} trỏ vào địa chỉ trình
 * duyệt gọi được. Chữ ký SigV4 phủ cả tên máy chủ, nên ký bằng {@code http://minio:9000} rồi giao
 * cho trình duyệt là nhận {@code SignatureDoesNotMatch} — xem javadoc {@link MinioProperties}. Khi
 * hai địa chỉ trùng nhau (máy phát triển, và bài kiểm tích hợp) thì chỉ có một đối tượng, không
 * tốn gì.
 *
 * <h2>Ngoại lệ của SDK KHÔNG lọt ra ngoài lớp này</h2>
 * SDK ném mười kiểu ngoại lệ kiểm tra ({@code ErrorResponseException},
 * {@code InsufficientDataException}, {@code XmlParserException}…). Để chúng đi lên tầng ứng dụng
 * nghĩa là {@code MediaUploadService} phải biết về giao thức S3 — và tệ hơn, một
 * {@code catch (Exception)} ở đâu đó sẽ nuốt luôn cả {@code NoSuchKey} lẫn "kho sập" rồi trả cùng
 * một câu. Ở đây chúng được dịch thành hai thứ khác nhau: <b>"không có đối tượng"</b> là một
 * {@link Optional#empty()} (một câu trả lời hợp lệ), còn mọi thứ khác là
 * {@link ObjectStorageException} (một sự cố).
 */
@Component
public class MinioObjectStorageAdapter implements ObjectStoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageAdapter.class);

    /** Mã lỗi S3 cho "khoá không tồn tại". Hai biến thể, tuỳ máy chủ và tuỳ lệnh. */
    private static final String NO_SUCH_KEY = "NoSuchKey";
    private static final String NOT_FOUND = "NoSuchObject";

    private final MinioClient apiClient;
    private final MinioClient signingClient;
    private final MinioProperties props;

    public MinioObjectStorageAdapter(MinioProperties props) {
        this.props = props;
        this.apiClient = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        this.signingClient = props.hasSeparatePublicEndpoint()
                ? MinioClient.builder()
                    .endpoint(props.getPublicEndpoint())
                    .credentials(props.getAccessKey(), props.getSecretKey())
                    .build()
                : this.apiClient;
    }

    @Override
    public String bucket() {
        return props.getBucket();
    }

    @Override
    public String presignPut(String objectKey, Duration ttl) {
        return presign(Method.PUT, objectKey, ttl);
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        return presign(Method.GET, objectKey, ttl);
    }

    private String presign(Method method, String objectKey, Duration ttl) {
        try {
            return signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new ObjectStorageException("Khong ky duoc URL " + method + " cho khoa "
                    + objectKey, ex);
        }
    }

    @Override
    public Optional<StoredObject> stat(String objectKey) {
        try {
            StatObjectResponse stat = apiClient.statObject(StatObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
            return Optional.of(new StoredObject(stat.size(), stat.contentType()));
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                // KHONG phai su co: day chinh la ca "client goi xac nhan ma chua he tai gi len".
                return Optional.empty();
            }
            throw new ObjectStorageException("Loi khi doc sieu du lieu cua khoa " + objectKey, ex);
        } catch (Exception ex) {
            throw new ObjectStorageException("Loi khi doc sieu du lieu cua khoa " + objectKey, ex);
        }
    }

    @Override
    public byte[] readRange(String objectKey, long offset, int length) {
        if (length <= 0) {
            return new byte[0];
        }
        try (InputStream in = apiClient.getObject(GetObjectArgs.builder()
                .bucket(props.getBucket())
                .object(objectKey)
                .offset(offset)
                .length((long) length)
                .build())) {
            // readNBytes, KHONG phai readAllBytes: may chu co the tra ve nhieu hon neu mot ngay
            // nao do tham so length bi bo sot, va luc ay ta se nap ca mot video 100 MiB vao heap.
            return in.readNBytes(length);
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                return new byte[0];
            }
            throw new ObjectStorageException("Loi khi doc khoang byte cua khoa " + objectKey, ex);
        } catch (IOException | RuntimeException ex) {
            throw new ObjectStorageException("Loi khi doc khoang byte cua khoa " + objectKey, ex);
        } catch (Exception ex) {
            throw new ObjectStorageException("Loi khi doc khoang byte cua khoa " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            apiClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
            log.debug("Da xoa doi tuong {} khoi bucket {}", objectKey, props.getBucket());
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                // Idempotent: xoa mot khoa khong ton tai la thanh cong. Duong don goi lai nhieu
                // lan tren cung mot hang la chuyen binh thuong.
                return;
            }
            throw new ObjectStorageException("Khong xoa duoc khoa " + objectKey, ex);
        } catch (Exception ex) {
            throw new ObjectStorageException("Khong xoa duoc khoa " + objectKey, ex);
        }
    }

    private static boolean isMissing(ErrorResponseException ex) {
        String code = ex.errorResponse() == null ? null : ex.errorResponse().code();
        return NO_SUCH_KEY.equals(code) || NOT_FOUND.equals(code)
                || (ex.response() != null && ex.response().code() == 404);
    }

    /** Sự cố kho — <b>không</b> bao gồm ca "đối tượng không tồn tại", vốn là một câu trả lời. */
    public static class ObjectStorageException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ObjectStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
