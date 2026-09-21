package vn.giapha.content.domain;

import java.util.Locale;

/**
 * Trạng thái duyệt dùng chung cho {@link Post} và {@link Honour} — khớp {@code ck_post_status} và
 * {@code ck_honour_status} của V17.
 *
 * <h2>Một máy trạng thái, hai aggregate</h2>
 * <pre>
 *   Bài viết :  DRAFT ──submit──▶ PENDING ──approve──▶ PUBLISHED ──withdraw──▶ WITHDRAWN
 *                 ▲                  │                     │
 *                 └──── reject ──────┘                     │
 *                 └────────────── withdraw ────────────────┘   (bỏ bản nháp)
 *
 *   Vinh danh:                    PENDING ──approve──▶ PUBLISHED
 *                                    │                     │
 *                                    └──── reject ────▶ WITHDRAWN ◀── withdraw
 * </pre>
 *
 * <p><b>Vinh danh không có {@link #DRAFT}</b>, và đó là khác biệt duy nhất giữa hai đường. Bài viết
 * cần nháp vì người viết là bác 60 tuổi gõ dở rồi hết phiên (§2: "mất một bài viết dở là cách chắc
 * chắn nhất để người ta không viết bài thứ hai"). Một vinh danh thì gồm bốn ô và điền xong trong
 * một phút; thêm trạng thái nháp cho nó chỉ tạo ra một hàng đợi thứ hai mà không ai mở.</p>
 *
 * <h2>{@link #WITHDRAWN} là trạng thái cuối, và nó KHÔNG phải "đã xoá"</h2>
 * Xoá mềm tuyệt đối: một bài đã lên trang chủ rồi bị gỡ là dữ kiện phải tra lại được — ai gỡ, lúc
 * nào, vì sao. Muốn đăng lại thì soạn bài mới; cho phép hồi sinh một bài đã gỡ nghĩa là nội dung
 * trên trang chủ đổi mà {@code audit_log} chỉ thấy một dòng {@code UPDATE}.
 */
public enum ContentStatus {

    /** Bản nháp — chỉ chính tác giả nhìn thấy. Không tồn tại với vinh danh. */
    DRAFT,

    /** Chờ Trưởng cành/chi/họ duyệt. */
    PENDING,

    /** Đã đăng. */
    PUBLISHED,

    /** Đã gỡ / bị từ chối hẳn. Trạng thái <b>cuối</b>. */
    WITHDRAWN;

    /** Trạng thái cuối: không lối ra nào. */
    public boolean isFinal() {
        return this == WITHDRAWN;
    }

    /** Đang nằm trong hàng đợi của người duyệt. */
    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isPublished() {
        return this == PUBLISHED;
    }

    /**
     * Phân giải từ chuỗi (tham số truy vấn, cột CSDL).
     *
     * @throws IllegalArgumentException với tên trạng thái rõ ràng; {@code null}/rỗng trả
     *         {@code null} để bên gọi hiểu là "không lọc theo trạng thái"
     */
    public static ContentStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Trang thai khong hop le: " + raw + "; chi nhan DRAFT, PENDING, PUBLISHED,"
                            + " WITHDRAWN", ex);
        }
    }
}
