package vn.giapha.clan.application;

/**
 * Mã lỗi máy đọc của context {@code clan} — thuộc tính {@code code} của RFC 7807.
 *
 * <p>Danh sách đóng, khớp từng ký tự với enum {@code ProblemCode} của
 * {@code contracts/openapi.yaml}. Hai mã <b>mới</b> ({@link #CLAN_NOT_INITIALIZED},
 * {@link #CLAN_ROOT_LOCKED}); phần còn lại dùng lại nguyên văn mã đã có — giao diện phân nhánh theo
 * {@code code}, nên một mã song song cho cùng một ý là một nhánh thừa ở phía client.</p>
 */
public final class ClanProblemCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /**
     * Chưa có hồ sơ dòng họ — HTTP 404 ở {@code GET /admin/clan-profile}.
     *
     * <p>Tách khỏi {@code NOT_FOUND} vì hành động tiếp theo khác hẳn: không phải "đường dẫn sai" mà
     * là "mở biểu mẫu khởi tạo". Giao diện rẽ nhánh theo mã này để vào thẳng màn khởi tạo.</p>
     */
    public static final String CLAN_NOT_INITIALIZED = "CLAN_NOT_INITIALIZED";

    /**
     * Đổi {@code rootBranchPath} khi phả đã có nhân khẩu (kể cả đã xoá mềm) — HTTP 409.
     * Xem {@code ClanProfile} về vì sao nhãn gốc bị khoá.
     */
    public static final String CLAN_ROOT_LOCKED = "CLAN_ROOT_LOCKED";

    /** Phiên bản gửi qua {@code If-Match} lệch phiên bản hiện tại — HTTP 409. */
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";

    /** Sửa hồ sơ đã có mà thiếu/sai {@code If-Match} — HTTP 412, cùng luật {@code PATCH /persons/{id}}. */
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";

    private ClanProblemCodes() {
    }
}
