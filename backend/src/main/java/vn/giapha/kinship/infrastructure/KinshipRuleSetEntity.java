package vn.giapha.kinship.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ánh xạ bảng {@code kinship_rule_set} (migration V3).
 *
 * <p>Cột phải khớp <b>từng chữ</b> với migration: {@code spring.jpa.hibernate.ddl-auto=validate}
 * nên sai một tên cột là cả ứng dụng không khởi động được, không riêng gì context này.</p>
 *
 * <p>Chuỗi kế thừa để ở dạng {@code parentId} trần, và danh sách luật nạp bằng truy vấn riêng thay
 * vì {@code @OneToMany}. Cố ý ít phép màu JPA: việc dựng cây kế thừa và hợp nhất luật nằm ở
 * {@link RuleSetJpaRepository} và ở domain, nơi test được không cần CSDL.</p>
 */
@Entity
@Table(name = "kinship_rule_set")
public class KinshipRuleSetEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "region", length = 10)
    private String region;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KinshipRuleSetEntity() {
    }

    public KinshipRuleSetEntity(UUID id, String code, String name, String scope, UUID parentId,
            String region, UUID branchId, String description, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.scope = scope;
        this.parentId = parentId;
        this.region = region;
        this.branchId = branchId;
        this.description = description;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

}
