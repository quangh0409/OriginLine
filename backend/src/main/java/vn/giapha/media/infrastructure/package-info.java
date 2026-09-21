/**
 * Hiện thực các port của {@code media}: adapter MinIO, adapter JPA, bộ lập lịch dọn tệp.
 *
 * <p>Không {@code @NamedInterface}. Gói {@code minio} là <b>nơi duy nhất trong cả cây nguồn</b>
 * được phép {@code import io.minio}; ràng buộc ấy cũng được ghi ngay trên khối khai báo phụ thuộc
 * trong {@code pom.xml}, vì đó là chỗ người ta nhìn khi định thêm một thư viện thứ hai.</p>
 */
package vn.giapha.media.infrastructure;
