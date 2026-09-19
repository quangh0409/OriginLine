package vn.giapha.dataimport.domain;

/**
 * Hành động dự kiến của một dòng khi lô được ghi vào phả.
 *
 * <p>Suy ra từ {@code person_external_ref}: tra {@code (EXCEL_MA, branch, Mã)} — có thì
 * {@link #UPDATE}, không có thì {@link #CREATE}. Đây là toàn bộ cơ chế bảo đảm
 * <b>tải lại không sinh người trùng</b>.</p>
 */
public enum PlannedAction {

    /** Chưa có ai mang mã này trong chi — sẽ tạo nhân khẩu mới. */
    CREATE,

    /** Đã có người mang mã này — sẽ cập nhật, <b>không</b> tạo thêm. */
    UPDATE,

    /** Bỏ qua (dòng ví dụ còn sót, dòng rỗng). */
    SKIP
}
