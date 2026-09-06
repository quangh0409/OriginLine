package vn.giapha.shared.exception;

/**
 * Không tìm thấy tài nguyên — ánh xạ sang HTTP 404.
 *
 * <p>Lưu ý nghiệp vụ: nhân khẩu bị <b>xóa mềm</b> vẫn tồn tại trong cây; chỉ ném ngoại lệ này khi
 * bản ghi thật sự không tồn tại, đừng dùng nó để che người đã bị đánh dấu xoá.</p>
 */
public class NotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    /** Tiện dụng: {@code new NotFoundException("Person", id)} → "Person 123 khong ton tai". */
    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(
                resource.toLowerCase() + ".not-found",
                "Khong tim thay " + resource + " voi dinh danh '" + id + "'");
    }
}
