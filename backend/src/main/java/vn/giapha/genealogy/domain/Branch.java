package vn.giapha.genealogy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Chi / Ngành / Cành / Nhánh</b> — bốn cấp phân nhánh của gia phả Việt, cộng cấp gốc dòng họ.
 *
 * <p>{@link #path()} là <b>chiều phân quyền hạng nhất</b>: Trưởng Chi chỉ thao tác được trên cây
 * con nằm dưới path được giao. Vì thế mọi kiểm quyền phải soi path, không chỉ soi vai trò.</p>
 *
 * <p><b>Bẫy ltree:</b> nhãn {@code ltree} chỉ nhận {@code [A-Za-z0-9_]}. {@link #name()} là tên
 * hiển thị <b>có dấu</b> ("Chi Thượng"), {@link #slug()} là nhãn không dấu ("chi_thuong"), và
 * {@code path} <b>luôn</b> ghép từ slug — không bao giờ từ name. Ràng buộc
 * {@code ck_branch_path_tail_is_slug} ở V2 canh điều này ở phía CSDL.</p>
 *
 * <p><b>Chức danh tách khỏi vai kỹ thuật:</b> {@link #headPersonId()} là <i>Trưởng chi</i> theo
 * huyết thống/đích tôn — hoàn toàn khác vai {@code BRANCH_HEAD} trong JWT. Một người có thể giữ
 * cả hai, hoặc chỉ một trong hai.</p>
 */
public class Branch {

    private final UUID id;
    private String name;
    private String slug;
    private BranchPath path;
    private UUID parentId;
    private BranchKind kind;
    private Region region;
    private UUID headPersonId;
    private Integer foundedYear;
    private String note;
    private int sortOrder;
    private boolean deleted;
    private Instant updatedAt;
    private long version;

    private Branch(UUID id) {
        this.id = Objects.requireNonNull(id, "Branch.id khong duoc null");
    }

    public static Branch create(UUID id, String name, BranchPath path, UUID parentId, BranchKind kind) {
        Branch branch = new Branch(id);
        branch.name = Objects.requireNonNull(name, "Branch.name khong duoc null");
        branch.path = Objects.requireNonNull(path, "Branch.path khong duoc null");
        branch.slug = path.leaf();
        branch.parentId = parentId;
        branch.kind = kind == null ? BranchKind.CHI : kind;
        branch.sortOrder = 0;
        branch.deleted = false;
        return branch;
    }

    public static Builder rehydrate(UUID id) {
        return new Builder(id);
    }

    /** Bổ nhiệm Trưởng chi — chức danh dòng tộc, không cấp thêm bất kỳ quyền kỹ thuật nào. */
    public void appointHead(UUID personId) {
        this.headPersonId = personId;
    }

    /** {@code true} nếu chi này chứa (hoặc chính là) chi kia — tương đương toán tử {@code @>}. */
    public boolean contains(Branch other) {
        return other != null && path.isAncestorOf(other.path);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public BranchPath path() {
        return path;
    }

    public UUID parentId() {
        return parentId;
    }

    public BranchKind kind() {
        return kind;
    }

    public Region region() {
        return region;
    }

    public UUID headPersonId() {
        return headPersonId;
    }

    public Integer foundedYear() {
        return foundedYear;
    }

    public String note() {
        return note;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Branch branch && id.equals(branch.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Bộ dựng cho việc nạp lại từ CSDL. */
    public static final class Builder {

        private final Branch branch;

        private Builder(UUID id) {
            this.branch = new Branch(id);
        }

        public Builder name(String value) {
            branch.name = value;
            return this;
        }

        public Builder slug(String value) {
            branch.slug = value;
            return this;
        }

        public Builder path(BranchPath value) {
            branch.path = value;
            return this;
        }

        public Builder parentId(UUID value) {
            branch.parentId = value;
            return this;
        }

        public Builder kind(BranchKind value) {
            branch.kind = value == null ? BranchKind.CHI : value;
            return this;
        }

        public Builder region(Region value) {
            branch.region = value;
            return this;
        }

        public Builder headPersonId(UUID value) {
            branch.headPersonId = value;
            return this;
        }

        public Builder foundedYear(Integer value) {
            branch.foundedYear = value;
            return this;
        }

        public Builder note(String value) {
            branch.note = value;
            return this;
        }

        public Builder sortOrder(int value) {
            branch.sortOrder = value;
            return this;
        }

        public Builder deleted(boolean value) {
            branch.deleted = value;
            return this;
        }

        public Builder updatedAt(Instant value) {
            branch.updatedAt = value;
            return this;
        }

        public Builder version(long value) {
            branch.version = value;
            return this;
        }

        public Branch build() {
            return branch;
        }
    }
}
