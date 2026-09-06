package vn.giapha.genealogy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Một cạnh quan hệ trong phả hệ.
 *
 * <p><b>Nguồn chân lý là cạnh trong đồ thị AGE</b>; lớp này (và bảng {@code relationship}) là
 * <b>bản chiếu</b> tồn tại để có khoá ngoại, nhật ký thay đổi và truy vấn SQL thuần. Hai bên
 * <b>bắt buộc</b> được ghi trong cùng một transaction; khi lệch nhau thì graph thắng.</p>
 *
 * <p>Hướng cạnh xem {@link RelType}. Vài ràng buộc lớp này tự bảo vệ, khớp với các CHECK ở V2:</p>
 * <ul>
 *   <li>không tự nối với chính mình ({@code ck_relationship_no_self});</li>
 *   <li>{@code spouseOrder} chỉ có nghĩa với {@code SPOUSE} và bắt đầu từ 1 (vợ cả/chồng cả);</li>
 *   <li>{@code heirKind} bắt buộc có với {@code HEIR} và bắt buộc vắng với loại khác.</li>
 * </ul>
 */
public class Relationship {

    private final UUID id;
    private final UUID fromPersonId;
    private final UUID toPersonId;
    private final RelType relType;
    private Integer spouseOrder;
    private HeirKind heirKind;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String endReason;
    private String note;
    private boolean deleted;
    private Instant deletedAt;

    private Relationship(UUID id, UUID fromPersonId, UUID toPersonId, RelType relType) {
        this.id = Objects.requireNonNull(id, "Relationship.id khong duoc null");
        this.fromPersonId = Objects.requireNonNull(fromPersonId, "from khong duoc null");
        this.toPersonId = Objects.requireNonNull(toPersonId, "to khong duoc null");
        this.relType = Objects.requireNonNull(relType, "relType khong duoc null");
        if (fromPersonId.equals(toPersonId)) {
            throw new IllegalArgumentException("Mot nguoi khong the co quan he voi chinh minh");
        }
    }

    public static Relationship parent(UUID id, UUID parentId, UUID childId, boolean adopted) {
        return new Relationship(id, parentId, childId, adopted ? RelType.PARENT_ADOPT : RelType.PARENT_BIO);
    }

    public static Relationship spouse(UUID id, UUID first, UUID second, Integer spouseOrder,
                                      LocalDate validFrom, LocalDate validTo) {
        Relationship rel = new Relationship(id, first, second, RelType.SPOUSE);
        rel.spouseOrder(spouseOrder);
        rel.validFrom = validFrom;
        rel.validTo = validTo;
        return rel;
    }

    public static Relationship heir(UUID id, UUID ancestorId, UUID heirId, HeirKind kind) {
        Relationship rel = new Relationship(id, ancestorId, heirId, RelType.HEIR);
        rel.heirKind(kind);
        return rel;
    }

    public static Relationship of(UUID id, UUID fromPersonId, UUID toPersonId, RelType relType) {
        return new Relationship(id, fromPersonId, toPersonId, relType);
    }

    public Relationship spouseOrder(Integer value) {
        if (value != null) {
            if (relType != RelType.SPOUSE) {
                throw new IllegalArgumentException("spouseOrder chi co nghia voi canh SPOUSE");
            }
            if (value < 1) {
                throw new IllegalArgumentException("spouseOrder bat dau tu 1 (vo ca/chong ca)");
            }
        }
        this.spouseOrder = value;
        return this;
    }

    public Relationship heirKind(HeirKind value) {
        if (relType == RelType.HEIR && value == null) {
            throw new IllegalArgumentException("Canh HEIR bat buoc co heirKind");
        }
        if (relType != RelType.HEIR && value != null) {
            throw new IllegalArgumentException("heirKind chi co nghia voi canh HEIR");
        }
        this.heirKind = value;
        return this;
    }

    public Relationship validity(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("validTo khong duoc truoc validFrom");
        }
        this.validFrom = from;
        this.validTo = to;
        return this;
    }

    public Relationship endReason(String value) {
        this.endReason = value;
        return this;
    }

    public Relationship note(String value) {
        this.note = value;
        return this;
    }

    /**
     * Xoá mềm bản chiếu. Khi cờ này bật thì cạnh AGE tương ứng <b>phải</b> bị xoá trong cùng
     * transaction — cạnh là nguồn chân lý, để lại cạnh mà xoá bản chiếu là tạo ra lệch dữ liệu.
     */
    public void softDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID fromPersonId() {
        return fromPersonId;
    }

    public UUID toPersonId() {
        return toPersonId;
    }

    public RelType relType() {
        return relType;
    }

    public Integer spouseOrder() {
        return spouseOrder;
    }

    public HeirKind heirKind() {
        return heirKind;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate validTo() {
        return validTo;
    }

    public String endReason() {
        return endReason;
    }

    public String note() {
        return note;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    /** Quan hệ hôn nhân còn hiệu lực hay đã kết thúc (ly hôn / một bên mất). */
    public boolean isCurrent() {
        return validTo == null;
    }

    /** Đầu kia của cạnh so với {@code personId}; {@code null} nếu người này không nằm trên cạnh. */
    public UUID otherEnd(UUID personId) {
        if (fromPersonId.equals(personId)) {
            return toPersonId;
        }
        if (toPersonId.equals(personId)) {
            return fromPersonId;
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Relationship rel && id.equals(rel.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
