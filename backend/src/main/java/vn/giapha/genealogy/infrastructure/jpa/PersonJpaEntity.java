package vn.giapha.genealogy.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản chiếu của bảng {@code person} (V2 §2.2).
 *
 * <h2>Vài chỗ dễ sai, đã chốt ở đây</h2>
 * <ul>
 *   <li>{@code version} do Hibernate quản; trigger {@code tg_person_touch} cố ý <b>không</b> đụng
 *       tới cột này. Trigger tăng thêm một lần nữa thì lần cập nhật kế tiếp ném
 *       {@code OptimisticLockException} giả.</li>
 *   <li>Bảng {@code person} không có cột cho nghề nghiệp, tiểu sử, liên hệ hay ảnh. Chúng nằm
 *       trong {@code attributes} (jsonb) dưới khoá dành riêng {@code _profile} — xem
 *       {@code PersonMapper}. Đây là quyết định của W2 vì migration thuộc sở hữu của W1.</li>
 *   <li>Ngày mất phải vắng khi {@code is_alive = true} ({@code ck_person_alive_vs_death}); domain
 *       đã canh trước nên tới đây chỉ còn việc ghi.</li>
 * </ul>
 */
@Entity
@Table(name = "person")
public class PersonJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "generation")
    private Integer generation;

    @Column(name = "birth_order")
    private Integer birthOrder;

    @Column(name = "birth_solar")
    private LocalDate birthSolar;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonbAttributeConverter.class)
    @Column(name = "birth_lunar")
    private Map<String, Object> birthLunar;

    @Column(name = "death_solar")
    private LocalDate deathSolar;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonbAttributeConverter.class)
    @Column(name = "death_lunar")
    private Map<String, Object> deathLunar;

    @Column(name = "is_alive", nullable = false)
    private boolean alive = true;

    @Column(name = "native_place", length = 255)
    private String nativePlace;

    @Column(name = "current_place", length = 255)
    private String currentPlace;

    @Column(name = "primary_branch_id")
    private UUID primaryBranchId;

    @Column(name = "lineage_status", nullable = false, length = 16)
    private String lineageStatus = "NORMAL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonbAttributeConverter.class)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(name = "privacy_level", nullable = false, length = 12)
    private String privacyLevel = "DEFAULT";

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "is_anonymized", nullable = false)
    private boolean anonymized;

    @Column(name = "anonymized_at")
    private OffsetDateTime anonymizedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PersonJpaEntity() {
    }

    public PersonJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getGeneration() {
        return generation;
    }

    public void setGeneration(Integer generation) {
        this.generation = generation;
    }

    public Integer getBirthOrder() {
        return birthOrder;
    }

    public void setBirthOrder(Integer birthOrder) {
        this.birthOrder = birthOrder;
    }

    public LocalDate getBirthSolar() {
        return birthSolar;
    }

    public void setBirthSolar(LocalDate birthSolar) {
        this.birthSolar = birthSolar;
    }

    public Map<String, Object> getBirthLunar() {
        return birthLunar;
    }

    public void setBirthLunar(Map<String, Object> birthLunar) {
        this.birthLunar = birthLunar;
    }

    public LocalDate getDeathSolar() {
        return deathSolar;
    }

    public void setDeathSolar(LocalDate deathSolar) {
        this.deathSolar = deathSolar;
    }

    public Map<String, Object> getDeathLunar() {
        return deathLunar;
    }

    public void setDeathLunar(Map<String, Object> deathLunar) {
        this.deathLunar = deathLunar;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public String getNativePlace() {
        return nativePlace;
    }

    public void setNativePlace(String nativePlace) {
        this.nativePlace = nativePlace;
    }

    public String getCurrentPlace() {
        return currentPlace;
    }

    public void setCurrentPlace(String currentPlace) {
        this.currentPlace = currentPlace;
    }

    public UUID getPrimaryBranchId() {
        return primaryBranchId;
    }

    public void setPrimaryBranchId(UUID primaryBranchId) {
        this.primaryBranchId = primaryBranchId;
    }

    public String getLineageStatus() {
        return lineageStatus;
    }

    public void setLineageStatus(String lineageStatus) {
        this.lineageStatus = lineageStatus;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : attributes;
    }

    public String getPrivacyLevel() {
        return privacyLevel;
    }

    public void setPrivacyLevel(String privacyLevel) {
        this.privacyLevel = privacyLevel;
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

    public boolean isAnonymized() {
        return anonymized;
    }

    public void setAnonymized(boolean anonymized) {
        this.anonymized = anonymized;
    }

    public OffsetDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public void setAnonymizedAt(OffsetDateTime anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
