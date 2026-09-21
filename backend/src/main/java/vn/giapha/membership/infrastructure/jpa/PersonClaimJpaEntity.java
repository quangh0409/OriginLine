package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.giapha.membership.domain.ClaimDuplicateSuspect;

/**
 * Bản chiếu JPA của {@code person_claim} (V16).
 *
 * <p>{@code screening} là {@code jsonb}: {@code @JdbcTypeCode(SqlTypes.JSON)} để Hibernate 6 gửi
 * đúng kiểu cho driver, {@code @Convert} lo phần chuyển đổi Java ⇄ chuỗi JSON.</p>
 *
 * <h2>Gần như mọi cột đều {@code updatable = false}</h2>
 * Nội dung một lá đơn <b>không đổi</b> sau khi gửi: loại đơn, người gửi, nhân khẩu được nhận, khai
 * báo, số điện thoại, ảnh chụp dò trùng. Sửa được nội dung đơn sau khi Trưởng chi đã đọc là mở đúng
 * lối "đánh tráo sau khi được duyệt". Chỉ máy trạng thái mới đổi: {@code status},
 * {@code reviewer_id}, {@code review_note}, {@code reviewed_at}, và {@code created_person_id} —
 * cái cuối chỉ được ghi đúng một lần, lúc duyệt.
 *
 * <p>Muốn sửa đơn thì rút rồi gửi đơn mới; lúc ấy có hai bản ghi và nhật ký nói được điều gì đã
 * xảy ra.</p>
 */
@Entity
@Table(name = "person_claim")
public class PersonClaimJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private String kind;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    @Column(name = "relative_person_id", updatable = false)
    private UUID relativePersonId;

    @Column(name = "relative_kind", updatable = false, length = 8)
    private String relativeKind;

    @Column(name = "declared_name", updatable = false, length = 160)
    private String declaredName;

    @Column(name = "declared_birth_year", updatable = false)
    private Integer declaredBirthYear;

    @Column(name = "declared_gender", updatable = false, length = 8)
    private String declaredGender;

    @Column(name = "target_branch_id", updatable = false)
    private UUID targetBranchId;

    @Column(name = "phone", nullable = false, updatable = false, length = 32)
    private String phone;

    @Column(name = "introduction", updatable = false)
    private String introduction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = ClaimScreeningJsonbConverter.class)
    @Column(name = "screening", nullable = false, updatable = false)
    private List<ClaimDuplicateSuspect> screening;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_person_id")
    private UUID createdPersonId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PersonClaimJpaEntity() {
    }

    @SuppressWarnings("java:S107")
    public PersonClaimJpaEntity(UUID id, String kind, UUID requestedBy, UUID personId,
                                UUID relativePersonId, String relativeKind, String declaredName,
                                Integer declaredBirthYear, String declaredGender,
                                UUID targetBranchId, String phone, String introduction,
                                List<ClaimDuplicateSuspect> screening) {
        this.id = id;
        this.kind = kind;
        this.requestedBy = requestedBy;
        this.personId = personId;
        this.relativePersonId = relativePersonId;
        this.relativeKind = relativeKind;
        this.declaredName = declaredName;
        this.declaredBirthYear = declaredBirthYear;
        this.declaredGender = declaredGender;
        this.targetBranchId = targetBranchId;
        this.phone = phone;
        this.introduction = introduction;
        this.screening = screening == null ? List.of() : List.copyOf(screening);
    }

    public UUID getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public UUID getPersonId() {
        return personId;
    }

    public UUID getRelativePersonId() {
        return relativePersonId;
    }

    public String getRelativeKind() {
        return relativeKind;
    }

    public String getDeclaredName() {
        return declaredName;
    }

    public Integer getDeclaredBirthYear() {
        return declaredBirthYear;
    }

    public String getDeclaredGender() {
        return declaredGender;
    }

    public UUID getTargetBranchId() {
        return targetBranchId;
    }

    public String getPhone() {
        return phone;
    }

    public String getIntroduction() {
        return introduction;
    }

    public List<ClaimDuplicateSuspect> getScreening() {
        return screening;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public UUID getCreatedPersonId() {
        return createdPersonId;
    }

    public void setCreatedPersonId(UUID createdPersonId) {
        this.createdPersonId = createdPersonId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
