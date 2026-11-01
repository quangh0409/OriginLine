package vn.giapha.clan.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hồ sơ dòng họ — <b>dòng đầu tiên của bảng {@code clan}</b> ở bước 2.
 *
 * <h2>Nhãn gốc bị khoá khi phả đã có người</h2>
 * {@link #revise} từ chối đổi {@link RootBranchLabel} khi {@code personCount > 0}. Luật này ở
 * domain chứ không ở service vì nó là <b>bất biến của hồ sơ</b>, không phải một chi tiết điều phối:
 * nhãn gốc là tiền tố của mọi {@code branch.path}, tức là phạm vi RBAC của mọi Trưởng chi, là khoá
 * của mẫu Excel đã phát ra, là dữ liệu trong các ảnh chụp chi của bài viết/đơn tự nhận. Đổi nó sau
 * khi đã nhập người là đổi địa chỉ của cả dòng họ dưới chân những thứ đang trỏ vào địa chỉ cũ.
 * Đếm cả nhân khẩu đã <b>xoá mềm</b>: xoá mềm giữ node trong cây, nên họ vẫn là "người đã có".
 *
 * <p>POJO thuần. Bản chiếu JPA nằm ở {@code clan.infrastructure.jpa}.</p>
 */
public final class ClanProfile {

    private final UUID id;
    private ClanProfileContent content;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public ClanProfile(UUID id, ClanProfileContent content, Instant createdAt, Instant updatedAt,
                       long version) {
        this.id = Objects.requireNonNull(id, "ClanProfile.id khong duoc null");
        this.content = Objects.requireNonNull(content, "ClanProfile.content khong duoc null");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Hồ sơ mới toanh — lần "Khởi tạo dòng họ" đầu tiên. */
    public static ClanProfile create(UUID id, ClanProfileContent content) {
        return new ClanProfile(id, content, null, null, 0L);
    }

    /**
     * Thay nội dung hồ sơ.
     *
     * @param next        nội dung mới (thay thế trọn vẹn)
     * @param personCount số nhân khẩu hiện có, <b>kể cả đã xoá mềm</b>
     * @throws RootLockedException khi đổi nhãn gốc trong lúc phả đã có người
     */
    public void revise(ClanProfileContent next, long personCount) {
        Objects.requireNonNull(next, "Noi dung ho so moi khong duoc null");
        if (next.rootDiffersFrom(content) && personCount > 0) {
            throw new RootLockedException(content.root(), next.root(), personCount);
        }
        this.content = next;
    }

    public UUID id() {
        return id;
    }

    public ClanProfileContent content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    /** Đổi nhãn gốc khi phả đã có người — xem javadoc lớp. */
    public static final class RootLockedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient RootBranchLabel current;

        public RootLockedException(RootBranchLabel current, RootBranchLabel requested,
                                   long personCount) {
            super("Không đổi được nhãn gốc từ '" + current + "' sang '" + requested
                    + "': phả đã có " + personCount + " nhân khẩu");
            this.current = current;
        }

        public RootBranchLabel current() {
            return current;
        }
    }
}
