package vn.giapha.genealogy.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản chiếu bảng {@code relationship}.
 *
 * <p><b>Đây không phải nguồn chân lý.</b> Nguồn chân lý là cạnh trong đồ thị {@code giapha_graph}.
 * Bảng này tồn tại để có khoá ngoại, nhật ký thay đổi và truy vấn SQL thuần. Lệnh ghi ở đây và
 * lệnh ghi cạnh AGE <b>phải</b> nằm trong cùng một {@code @Transactional}; khi hai bên lệch nhau
 * thì graph thắng.</p>
 */
@Entity
@Table(name = "relationship")
public class RelationshipJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "from_person_id", nullable = false)
    private UUID fromPersonId;

    @Column(name = "to_person_id", nullable = false)
    private UUID toPersonId;

    @Column(name = "rel_type", nullable = false, length = 20)
    private String relType;

    @Column(name = "spouse_order")
    private Integer spouseOrder;

    @Column(name = "heir_type", length = 16)
    private String heirType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "end_reason", length = 16)
    private String endReason;

    @Column(name = "note")
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonbAttributeConverter.class)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RelationshipJpaEntity() {
    }

    public RelationshipJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFromPersonId() {
        return fromPersonId;
    }

    public void setFromPersonId(UUID fromPersonId) {
        this.fromPersonId = fromPersonId;
    }

    public UUID getToPersonId() {
        return toPersonId;
    }

    public void setToPersonId(UUID toPersonId) {
        this.toPersonId = toPersonId;
    }

    public String getRelType() {
        return relType;
    }

    public void setRelType(String relType) {
        this.relType = relType;
    }

    public Integer getSpouseOrder() {
        return spouseOrder;
    }

    public void setSpouseOrder(Integer spouseOrder) {
        this.spouseOrder = spouseOrder;
    }

    public String getHeirType() {
        return heirType;
    }

    public void setHeirType(String heirType) {
        this.heirType = heirType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : attributes;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
