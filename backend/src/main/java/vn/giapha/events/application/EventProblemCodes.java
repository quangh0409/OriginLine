package vn.giapha.events.application;

/**
 * Mã lỗi ổn định của context {@code events}, đi trong trường {@code code} của thân RFC 7807.
 *
 * <p>Đây là <b>hợp đồng công khai</b> với giao diện: client phân nhánh xử lý theo mã này chứ không
 * theo câu tiếng Việt trong {@code detail}. Chỉ được thêm mới, không đổi và không xoá.</p>
 */
public final class EventProblemCodes {

    /** Thiếu hoặc sai {@code If-Match} khi sửa — HTTP 412. */
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";

    /** Có người khác vừa sửa sự kiện này kể từ {@code ETag} gửi lên — HTTP 409. */
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";

    /**
     * Ngày giỗ phải sửa trên hồ sơ nhân khẩu, không sửa trên bản ghi sự kiện — HTTP 409.
     *
     * <p>{@code person.death_lunar} là nguồn chân lý của ngày giỗ (BA v2). Cho phép ghi một ngày
     * khác vào bản ghi {@code GIO} là tạo ra một thay đổi <b>không có tác dụng</b>: bộ sinh lịch
     * nhắc vẫn lấy theo hồ sơ, nên người sửa tin rằng mình đã đổi ngày giỗ trong khi cả họ vẫn được
     * nhắc theo ngày cũ.</p>
     */
    public static final String GIO_DATE_FROM_PERSON = "GIO_DATE_FROM_PERSON";

    /** Sự kiện đã bị xoá mềm thì không sửa tiếp — HTTP 409. */
    public static final String EVENT_DELETED = "EVENT_DELETED";

    private EventProblemCodes() {
    }
}
