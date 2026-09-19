package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lời mời đích danh vào hệ thống — <b>mang sẵn nhân khẩu sẽ được ghép</b>.
 *
 * <h2>Vì sao {@link #personId} là bắt buộc, không phải tuỳ chọn</h2>
 * Đây là điểm thiết kế quan trọng nhất của cả luồng. Trưởng chi đã tự tay chọn người mình mời
 * trong phả trước khi phát mã; nếu lời mời không chở theo lựa chọn ấy thì người nhận đăng nhập
 * xong sẽ rơi vào trạng thái "chờ Hội đồng duyệt" — thứ mà tài liệu thiết kế phiên đầu tiên gọi
 * đích danh là điểm yếu nhất, và là bắt hệ thống hỏi lại một câu đã có đáp án.
 *
 * <h2>Bí mật một lần</h2>
 * Chỉ có băm ở đây; mã thô không tồn tại trong đối tượng này, trong CSDL, hay trong log. Nhận
 * thành công thì trạng thái chuyển {@link InvitationStatus#ACCEPTED} và không đảo lại được —
 * "dùng xong thì chết" là một bất biến của domain, không phải một phép kiểm ở tầng trên.
 *
 * <p>POJO thuần: không {@code @Entity}, không {@code @Component}. Bản chiếu JPA nằm ở
 * {@code membership.infrastructure.jpa}.</p>
 */
public final class Invitation {

    private final UUID id;
    private final String codeHash;
    private final UUID personId;
    private final UUID branchId;
    private final UUID invitedBy;
    private final Instant expiresAt;

    private InvitationStatus status;
    private UUID acceptedBy;
    private Instant acceptedAt;
    private Instant revokedAt;
    private String revokedReason;
    private final String note;
    private final Instant createdAt;
    private final long version;

    public Invitation(UUID id, String codeHash, UUID personId, UUID branchId, UUID invitedBy,
                      InvitationStatus status, Instant expiresAt, UUID acceptedBy,
                      Instant acceptedAt, Instant revokedAt, String revokedReason, String note,
                      Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "Invitation.id khong duoc null");
        this.codeHash = requireHash(codeHash);
        this.personId = Objects.requireNonNull(personId, "Invitation.personId khong duoc null");
        this.branchId = branchId;
        this.invitedBy = Objects.requireNonNull(invitedBy, "Invitation.invitedBy khong duoc null");
        this.status = status == null ? InvitationStatus.PENDING : status;
        this.expiresAt = Objects.requireNonNull(expiresAt, "Invitation.expiresAt khong duoc null");
        this.acceptedBy = acceptedBy;
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.note = note;
        this.createdAt = createdAt;
        this.version = version;
    }

    /** Lời mời mới phát. {@code codeHash} là băm của mã thô — mã thô không đi vào đây. */
    public static Invitation issue(UUID id, String codeHash, UUID personId, UUID branchId,
                                   UUID invitedBy, Instant expiresAt, String note) {
        return new Invitation(id, codeHash, personId, branchId, invitedBy,
                InvitationStatus.PENDING, expiresAt, null, null, null, null, note, null, 0L);
    }

    public UUID id() {
        return id;
    }

    public String codeHash() {
        return codeHash;
    }

    public UUID personId() {
        return personId;
    }

    public UUID branchId() {
        return branchId;
    }

    public UUID invitedBy() {
        return invitedBy;
    }

    public InvitationStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UUID acceptedBy() {
        return acceptedBy;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String revokedReason() {
        return revokedReason;
    }

    public String note() {
        return note;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }

    /**
     * Dùng được không, <b>xét tại thời điểm {@code now}</b>.
     *
     * <p>Nhận {@code now} làm tham số chứ không đọc {@link Instant#now()}: một domain POJO đọc đồng
     * hồ hệ thống là một domain POJO không kiểm chứng được ca "hết hạn" mà không phải ngủ 7 ngày.</p>
     */
    public InvitationUsability usabilityAt(Instant now) {
        if (status == InvitationStatus.ACCEPTED) {
            return InvitationUsability.ALREADY_USED;
        }
        if (status == InvitationStatus.REVOKED) {
            return InvitationUsability.REVOKED;
        }
        if (now != null && !now.isBefore(expiresAt)) {
            return InvitationUsability.EXPIRED;
        }
        return InvitationUsability.USABLE;
    }

    /**
     * Ghi nhận người nhận. Gọi khi lời mời <b>đã</b> được xác nhận còn dùng được.
     *
     * <p>Vẫn kiểm lại {@link #usabilityAt} ở đây, dù nơi gọi vừa kiểm xong: đây là lằn ranh giữa
     * "một lần" và "nhiều lần", và một bất biến mà chỉ tầng trên canh là một bất biến sẽ mất khi
     * có lối gọi thứ hai.</p>
     */
    public void accept(UUID appUserId, Instant now) {
        Objects.requireNonNull(appUserId, "appUserId khong duoc null khi nhan loi moi");
        InvitationUsability usability = usabilityAt(now);
        if (!usability.isUsable()) {
            throw new IllegalStateException("Loi moi khong dung duoc: " + usability);
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedBy = appUserId;
        this.acceptedAt = now;
    }

    /**
     * Thu hồi. Không ném khi lời mời đã hết hạn — thu hồi một mã quá hạn là vô hại và là điều
     * Trưởng chi làm khi dọn danh sách.
     *
     * @throws IllegalStateException khi lời mời <b>đã được nhận</b>: lúc ấy tài khoản đã gắn vào
     *         nhân khẩu rồi, và gỡ mối gắn ấy là một nghiệp vụ khác hẳn (đổi chủ tài khoản), phải
     *         để lại vết riêng chứ không được lẫn vào lối "thu hồi mã".
     */
    public void revoke(String reason, Instant now) {
        if (status == InvitationStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Loi moi da duoc nhan, khong thu hoi duoc — tai khoan da gan voi nhan khau");
        }
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    private static String requireHash(String codeHash) {
        if (codeHash == null || codeHash.length() != 64) {
            throw new IllegalArgumentException("code_hash phai la SHA-256 dang hex 64 ky tu");
        }
        return codeHash;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Invitation invitation && id.equals(invitation.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
