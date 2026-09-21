package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Một đơn <b>tự nhận mình trong phả</b> — hai loại, một máy trạng thái.
 *
 * <h2>Đơn chỉ là đơn: nó KHÔNG tạo nhân khẩu</h2>
 * Ràng buộc 1 của design 07 §1.5, và là bất biến nặng nhất của lớp này. Xoá mềm là luật tuyệt đối
 * của dự án — không bao giờ xoá cứng một nhân khẩu vì nó phá liên kết cây — nên nếu đơn
 * {@link PersonClaimKind#NEW_PERSON} tạo người ngay lúc gửi thì <b>mỗi đơn bị từ chối để lại một
 * node ma trong phả</b>. Vì vậy {@link #createdPersonId} chỉ được ghi ở {@link #approve}, và ràng
 * buộc {@code ck_person_claim_created} của V16 canh điều đó ở CSDL chứ không chỉ ở đây.
 *
 * <h2>{@link #targetBranchId} là ảnh chụp lúc gửi, và nó quyết định AI DUYỆT</h2>
 * Với đơn {@code EXISTING} đó là chi của nhân khẩu được nhận. Với đơn {@code NEW_PERSON} đó là chi
 * của <b>người thân được chỉ ra</b> — vì người mới chưa thuộc chi nào. Trưởng chi Ất không duyệt
 * được đơn trỏ vào người chi Bính; phép so là {@code ltree}, chạy ở
 * {@code BranchScopeGuard#requireReviewAccess}.
 *
 * <h2>Không ai được tự duyệt đơn của chính mình</h2>
 * Cùng luật với {@link ChangeRequest}. Ở đây nó nặng hơn một bậc: đơn này quyết định ai được gắn
 * vào hồ sơ của ai, tức quyền sửa phả và quyền đọc dữ liệu nhạy cảm của chính hồ sơ ấy.
 *
 * <p>POJO thuần: không {@code @Entity}, không {@code @Component}.</p>
 */
public final class PersonClaim {

    private final UUID id;
    private final PersonClaimKind kind;
    private final UUID requestedBy;

    /** Chỉ đơn {@code EXISTING}: nhân khẩu được nhận. */
    private final UUID personId;

    /** Chỉ đơn {@code NEW_PERSON}: người thân đã có trong phả, và quan hệ với họ. */
    private final UUID relativePersonId;
    private final RelativeKind relativeKind;

    private final String declaredName;
    private final Integer declaredBirthYear;
    private final Gender declaredGender;

    private final UUID targetBranchId;
    private final String phone;
    private final String introduction;
    private final List<ClaimDuplicateSuspect> screening;

    private PersonClaimStatus status;
    private UUID reviewerId;
    private String reviewNote;
    private Instant reviewedAt;
    private UUID createdPersonId;

    private final Instant createdAt;
    private final long version;

    @SuppressWarnings("java:S107")
    public PersonClaim(UUID id, PersonClaimKind kind, UUID requestedBy, UUID personId,
                       UUID relativePersonId, RelativeKind relativeKind, String declaredName,
                       Integer declaredBirthYear, Gender declaredGender, UUID targetBranchId,
                       String phone, String introduction, List<ClaimDuplicateSuspect> screening,
                       PersonClaimStatus status, UUID reviewerId, String reviewNote,
                       Instant reviewedAt, UUID createdPersonId, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "PersonClaim.id khong duoc null");
        this.kind = Objects.requireNonNull(kind, "PersonClaim.kind khong duoc null");
        this.requestedBy = Objects.requireNonNull(requestedBy,
                "PersonClaim.requestedBy khong duoc null");
        this.personId = personId;
        this.relativePersonId = relativePersonId;
        this.relativeKind = relativeKind;
        this.declaredName = declaredName;
        this.declaredBirthYear = declaredBirthYear;
        this.declaredGender = declaredGender;
        this.targetBranchId = targetBranchId;
        this.phone = requirePhone(phone);
        this.introduction = introduction;
        this.screening = screening == null ? List.of() : List.copyOf(screening);
        this.status = status == null ? PersonClaimStatus.PENDING : status;
        this.reviewerId = reviewerId;
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
        this.createdPersonId = createdPersonId;
        this.createdAt = createdAt;
        this.version = version;
        requireShape();
    }

    /** Đơn "tôi là người này trong phả". */
    public static PersonClaim nhanMinh(UUID id, UUID requestedBy, UUID personId,
                                       UUID targetBranchId, String phone, String introduction) {
        return new PersonClaim(id, PersonClaimKind.EXISTING, requestedBy, personId, null, null,
                null, null, null, targetBranchId, phone, introduction, null,
                PersonClaimStatus.PENDING, null, null, null, null, null, 0L);
    }

    /** Đơn "tôi chưa có trong phả" — mang khai báo và <b>bắt buộc</b> một người thân đã có. */
    @SuppressWarnings("java:S107")
    public static PersonClaim chuaCoTrongPha(UUID id, UUID requestedBy, String declaredName,
                                             Integer declaredBirthYear, Gender declaredGender,
                                             UUID relativePersonId, RelativeKind relativeKind,
                                             UUID targetBranchId, String phone,
                                             String introduction,
                                             List<ClaimDuplicateSuspect> screening) {
        return new PersonClaim(id, PersonClaimKind.NEW_PERSON, requestedBy, null, relativePersonId,
                relativeKind, declaredName, declaredBirthYear, declaredGender, targetBranchId,
                phone, introduction, screening, PersonClaimStatus.PENDING, null, null, null, null,
                null, 0L);
    }

    public UUID id() {
        return id;
    }

    public PersonClaimKind kind() {
        return kind;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public UUID personId() {
        return personId;
    }

    public UUID relativePersonId() {
        return relativePersonId;
    }

    public RelativeKind relativeKind() {
        return relativeKind;
    }

    public String declaredName() {
        return declaredName;
    }

    public Integer declaredBirthYear() {
        return declaredBirthYear;
    }

    public Gender declaredGender() {
        return declaredGender;
    }

    /** Chi đích — <b>căn cứ để so phạm vi {@code ltree} khi duyệt</b>. */
    public UUID targetBranchId() {
        return targetBranchId;
    }

    public String phone() {
        return phone;
    }

    public String introduction() {
        return introduction;
    }

    /**
     * Ảnh chụp kết quả dò trùng, chụp <b>lúc gửi</b> — ràng buộc 3 của design 07 §1.5.
     *
     * <p><b>Ảnh chụp, không phải dò lại lúc duyệt.</b> Trưởng chi phải thấy đúng thứ mà hệ thống đã
     * thấy khi nhận đơn, chứ không phải một kết quả khác vì phả đã đổi trong lúc chờ. Và dò lại ở
     * mỗi lần mở màn duyệt là một phép quét toàn dòng họ cho mỗi lượt cuộn danh sách.</p>
     *
     * <p>Rỗng là kết quả mong đợi với tuyệt đại đa số đơn, kể cả đơn của người trùng tên với ai đó
     * trong họ — xem bộ trọng số ở {@code DuplicateScorer}.</p>
     */
    public List<ClaimDuplicateSuspect> screening() {
        return screening;
    }

    public PersonClaimStatus status() {
        return status;
    }

    public UUID reviewerId() {
        return reviewerId;
    }

    public String reviewNote() {
        return reviewNote;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    /**
     * Nhân khẩu được tạo <b>lúc duyệt</b> một đơn {@code NEW_PERSON}.
     *
     * <p>Rỗng ở mọi đơn bị từ chối, và đó là bằng chứng kiểm được của ràng buộc 1: không có node
     * ma nào trong phả.</p>
     */
    public UUID createdPersonId() {
        return createdPersonId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }

    /** Nhân khẩu mà đơn này dẫn tới sau khi duyệt: người được nhận, hoặc người vừa được tạo. */
    public UUID resolvedPersonId() {
        return personId != null ? personId : createdPersonId;
    }

    /**
     * Duyệt.
     *
     * @param createdPerson nhân khẩu vừa được tạo, với đơn {@code NEW_PERSON}; {@code null} với
     *                      đơn {@code EXISTING}
     * @throws IllegalStateException khi đơn đã đóng, hoặc người duyệt chính là người gửi
     */
    public void approve(UUID reviewer, String note, Instant at, UUID createdPerson) {
        // KIEM TRUOC, DOI TRANG THAI SAU. Dao lai thi mot lan goi sai se de doi tuong o trang thai
        // APPROVED roi moi nem — va neu bat ngoai le ay o tang tren ma khong cuon transaction thi
        // don da "duyet" trong bo nho mot cach lang le.
        if (kind.taoNhanKhauKhiDuyet()) {
            Objects.requireNonNull(createdPerson, "Duyet don NEW_PERSON phai kem nhan khau vua tao");
        } else if (createdPerson != null) {
            throw new IllegalArgumentException(
                    "Don EXISTING khong tao nhan khau nao — no chi gan tai khoan vao nguoi da co");
        }
        transition(PersonClaimStatus.APPROVED, reviewer, note, at);
        if (kind.taoNhanKhauKhiDuyet()) {
            this.createdPersonId = createdPerson;
        }
    }

    /** Từ chối. Lý do là <b>bắt buộc</b> — người gửi có quyền biết vì sao để mà khai lại cho đúng. */
    public void reject(UUID reviewer, String note, Instant at) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Tu choi don phai kem ly do");
        }
        transition(PersonClaimStatus.REJECTED, reviewer, note, at);
    }

    /**
     * Người gửi tự rút lại. Không đi qua {@link #transition} vì không cần luật "không tự duyệt":
     * rút đơn của chính mình là việc hoàn toàn hợp lệ.
     */
    public void cancel(UUID by, Instant at) {
        requireOpen();
        if (!requestedBy.equals(by)) {
            throw new IllegalStateException("Chi nguoi gui moi duoc rut lai don cua minh");
        }
        this.status = PersonClaimStatus.CANCELLED;
        this.reviewerId = by;
        this.reviewedAt = at == null ? Instant.now() : at;
    }

    /**
     * <b>Không ai được tự duyệt đơn của chính mình.</b>
     *
     * <p>Ở đây luật ấy nặng hơn cả ở luồng đính chính: đơn này quyết định ai được gắn vào hồ sơ của
     * ai, tức quyền sửa phả và quyền đọc dữ liệu Tầng 3 của chính hồ sơ ấy. Một Trưởng chi tự duyệt
     * đơn nhận mình là một người tự trao cho mình một danh tính trong phả, và {@code audit_log} sẽ
     * ghi lại một cuộc "phê duyệt" không có ai kiểm tra ai.</p>
     */
    private void transition(PersonClaimStatus next, UUID reviewer, String note, Instant at) {
        requireOpen();
        Objects.requireNonNull(reviewer, "reviewerId khong duoc null khi xu ly don");
        if (reviewer.equals(requestedBy)) {
            throw new IllegalStateException("Khong duoc tu duyet don do chinh minh gui");
        }
        this.status = next;
        this.reviewerId = reviewer;
        this.reviewNote = note;
        this.reviewedAt = at == null ? Instant.now() : at;
    }

    private void requireOpen() {
        if (status.isFinal()) {
            throw new IllegalStateException(
                    "Don da o trang thai cuoi (" + status + "), khong xu ly lai duoc");
        }
    }

    /** Hình dạng của hai loại đơn — cùng luật với {@code ck_person_claim_shape} của V16. */
    private void requireShape() {
        if (kind == PersonClaimKind.EXISTING) {
            if (personId == null) {
                throw new IllegalArgumentException("Don EXISTING phai tro toi mot nhan khau co san");
            }
            if (relativePersonId != null || declaredName != null) {
                throw new IllegalArgumentException(
                        "Don EXISTING khong mang khai bao nguoi moi hay nguoi than");
            }
            return;
        }
        if (personId != null) {
            throw new IllegalArgumentException(
                    "Don NEW_PERSON KHONG duoc tro toi nhan khau nao luc gui — xem rang buoc 1");
        }
        if (relativePersonId == null || relativeKind == null) {
            throw new IllegalArgumentException(
                    "Don NEW_PERSON phai chi ra nguoi than da co trong pha (bo, me, hoac vo/chong)");
        }
        if (declaredName == null || declaredName.isBlank()) {
            throw new IllegalArgumentException("Don NEW_PERSON phai khai ho ten");
        }
    }

    private static String requirePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Don phai kem so dien thoai de Truong chi goi kiem chung");
        }
        return raw.trim();
    }

    /**
     * Ảnh chụp cho {@code audit_log}.
     *
     * <p><b>Không</b> chép {@link #phone} hay {@link #introduction} vào đây: cả hai là dữ liệu cá
     * nhân của một người đang sống, và {@code audit_log} là bảng chỉ ghi thêm — lọt vào là không gỡ
     * ra được. Đúng tinh thần "ghi tên trường, KHÔNG ghi giá trị" của V5.</p>
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.toString());
        snapshot.put("kind", kind.name());
        snapshot.put("status", status.name());
        snapshot.put("requestedBy", requestedBy.toString());
        snapshot.put("personId", personId == null ? null : personId.toString());
        snapshot.put("relativePersonId",
                relativePersonId == null ? null : relativePersonId.toString());
        snapshot.put("targetBranchId", targetBranchId == null ? null : targetBranchId.toString());
        snapshot.put("reviewerId", reviewerId == null ? null : reviewerId.toString());
        snapshot.put("createdPersonId",
                createdPersonId == null ? null : createdPersonId.toString());
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PersonClaim claim && id.equals(claim.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
