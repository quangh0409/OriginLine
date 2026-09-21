package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mã mời <b>dòng họ</b> — dùng nhiều lần, cấp cho cả họ, không trỏ vào nhân khẩu nào.
 *
 * <h2>Đây là một cơ chế riêng, không phải biến thể của {@link Invitation}</h2>
 * {@code invitation.person_id} là {@code NOT NULL}, và chính điều đó làm người nhận mã cá nhân
 * không phải chờ duyệt: Trưởng chi đã chỉ đích danh khi phát. Mã dòng họ thì ngược hẳn — nó chỉ mở
 * hai cửa (<b>đăng ký tài khoản</b> và <b>xem phả đồ</b>), và người dùng nó <i>vẫn</i> phải tự nhận
 * mình rồi chờ Trưởng chi duyệt. Nhồi hai nghiệp vụ vào một bảng thì {@code person_id} phải thành
 * nullable, và ngay lúc ấy bất biến "lời mời cá nhân không bao giờ rơi vào hàng chờ" mất chỗ đứng.
 *
 * <h2>Cấp mã cho cả họ nghĩa là mã mời MỚI LÀ ranh giới an toàn thật sự</h2>
 * Ai cầm được mã là đăng ký được và xem được danh sách người đang sống của dòng họ — tên, đời, quan
 * hệ. Và mã ấy <i>sẽ</i> lan: dán vào nhóm Zalo, chuyển tiếp, chụp màn hình. Bước duyệt kiểm soát
 * thứ khác (ai được gắn vào hồ sơ của ai). Vì chỉ còn <b>một</b> lớp bảo vệ, lớp ấy phải quản được,
 * nên <b>bốn chốt của design 07 §1.2 là bắt buộc chứ không phải tuỳ chọn</b>:
 * <ol>
 *   <li><b>Có hạn dùng</b> — {@link #expiresAt} là {@code NOT NULL}, không có giá trị "vô hạn";</li>
 *   <li><b>Thu hồi được</b> — {@link #revoke}, và nó không đụng tới người đã vào;</li>
 *   <li><b>Đếm lượt dùng</b> — {@link #useCount}, chốt quan trọng nhất và dễ bỏ qua nhất;</li>
 *   <li><b>Giới hạn tần suất</b> — ở {@code InviteThrottle}, ngoài lớp này.</li>
 * </ol>
 *
 * <h2>Bộ đếm KHÔNG được tăng ở đây</h2>
 * {@link #useCount} là <b>chỉ đọc</b> trên đối tượng này. Tăng bằng đọc-rồi-ghi thì hai người bấm
 * cùng lúc đếm thành một lượt, và một bộ đếm đếm thiếu còn tệ hơn không có bộ đếm vì nó tạo cảm
 * giác an toàn giả — Hội đồng nhìn thấy 200 trong khi thực tế là 400 và kết luận mã chưa rò. Phép
 * tăng là một câu {@code UPDATE ... SET use_count = use_count + 1} có điều kiện, nằm ở
 * {@code ClanInviteCodeRepository#tryConsume}.
 *
 * <p>POJO thuần: không {@code @Entity}, không {@code @Component}.</p>
 */
public final class ClanInviteCode {

    private final UUID id;
    private final String codeHash;
    private final String label;
    private final UUID issuedBy;
    private final Instant expiresAt;
    private final Integer maxUses;
    private final int useCount;

    private ClanInviteStatus status;
    private Instant revokedAt;
    private String revokedReason;
    private final String note;
    private final Instant createdAt;
    private final long version;

    @SuppressWarnings("java:S107")
    public ClanInviteCode(UUID id, String codeHash, String label, UUID issuedBy,
                          ClanInviteStatus status, Instant expiresAt, Integer maxUses,
                          int useCount, Instant revokedAt, String revokedReason, String note,
                          Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "ClanInviteCode.id khong duoc null");
        this.codeHash = requireHash(codeHash);
        this.label = label;
        this.issuedBy = Objects.requireNonNull(issuedBy, "ClanInviteCode.issuedBy khong duoc null");
        this.status = status == null ? ClanInviteStatus.ACTIVE : status;
        this.expiresAt = Objects.requireNonNull(expiresAt,
                "ClanInviteCode.expiresAt khong duoc null — ma khong han la ma vinh vien");
        this.maxUses = maxUses;
        this.useCount = Math.max(useCount, 0);
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.note = note;
        this.createdAt = createdAt;
        this.version = version;
    }

    /** Mã mới phát. {@code codeHash} là băm của mã thô — mã thô không đi vào đây. */
    public static ClanInviteCode issue(UUID id, String codeHash, String label, UUID issuedBy,
                                       Instant expiresAt, Integer maxUses, String note) {
        return new ClanInviteCode(id, codeHash, label, issuedBy, ClanInviteStatus.ACTIVE,
                expiresAt, maxUses, 0, null, null, note, null, 0L);
    }

    public UUID id() {
        return id;
    }

    public String codeHash() {
        return codeHash;
    }

    public String label() {
        return label;
    }

    public UUID issuedBy() {
        return issuedBy;
    }

    public ClanInviteStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /** Trần lượt dùng; {@code null} nghĩa là không giới hạn <i>số lượt</i> (vẫn có hạn thời gian). */
    public Integer maxUses() {
        return maxUses;
    }

    /**
     * <b>Chốt 3</b> — số lượt đã dùng. Chỉ đọc; xem javadoc lớp về việc vì sao không tăng ở đây.
     *
     * <p>Đây là con số Hội đồng nhìn vào để biết mã đã rò: 400 lượt trên một dòng họ 600 người là
     * một câu hỏi, và không có bộ đếm thì câu hỏi ấy không bao giờ được đặt ra.</p>
     */
    public int useCount() {
        return useCount;
    }

    /** Số lượt còn lại; {@code null} khi không đặt trần. */
    public Integer remainingUses() {
        return maxUses == null ? null : Math.max(maxUses - useCount, 0);
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
     * hồ hệ thống là một domain POJO không kiểm chứng được ca "hết hạn" mà không phải ngủ 30 ngày.</p>
     *
     * <p>Thứ tự kiểm có chủ ý: <b>thu hồi trước hết hạn trước hết lượt</b>. Một mã vừa bị thu hồi
     * vừa quá hạn thì lý do đáng nói với người dùng là thu hồi — nó là hành động của Hội đồng và
     * dẫn tới một câu hỏi khác hẳn.</p>
     */
    public ClanInviteUsability usabilityAt(Instant now) {
        if (status == ClanInviteStatus.REVOKED) {
            return ClanInviteUsability.REVOKED;
        }
        if (now != null && !now.isBefore(expiresAt)) {
            return ClanInviteUsability.EXPIRED;
        }
        if (maxUses != null && useCount >= maxUses) {
            return ClanInviteUsability.EXHAUSTED;
        }
        return ClanInviteUsability.USABLE;
    }

    /**
     * <b>Chốt 2</b> — thu hồi.
     *
     * <p>Không ném khi mã đã hết hạn: thu hồi một mã quá hạn là vô hại và là điều Hội đồng làm khi
     * dọn danh sách. Và <b>không</b> có phép đảo lại: mở lại một mã đã thu hồi là xoá mất lý do
     * người ta thu hồi nó. Cần mã mới thì phát mã mới — mã cũ vẫn nằm đó với bộ đếm của nó, và
     * chính con số ấy là thứ đáng giữ.</p>
     */
    public void revoke(String reason, Instant now) {
        this.status = ClanInviteStatus.REVOKED;
        this.revokedAt = now == null ? Instant.now() : now;
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
        return other instanceof ClanInviteCode code && id.equals(code.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
