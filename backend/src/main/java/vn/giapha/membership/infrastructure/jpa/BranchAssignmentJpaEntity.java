package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code branch_assignment}.
 *
 * <p>Cố ý dùng khoá thô ({@code role_id}, {@code branch_id}) chứ không {@code @ManyToOne}: tra một
 * phân công là việc chạy trên <i>mọi</i> request có kiểm quyền, và một quan hệ lazy ở đây là một
 * truy vấn N+1 nằm đúng trên đường đi nóng nhất của hệ thống. Việc nối sang {@code role.code} và
 * {@code branch.path} do một câu native query duy nhất lo — xem {@code BranchAssignmentJpaRepository}.</p>
 */
@Entity
@Table(name = "branch_assignment")
public class BranchAssignmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "note")
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BranchAssignmentJpaEntity() {
    }

    public BranchAssignmentJpaEntity(UUID id, UUID appUserId, UUID roleId) {
        this.id = id;
        this.appUserId = appUserId;
        this.roleId = roleId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
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

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getVersion() {
        return version;
    }
}
