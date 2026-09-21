package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.domain.ClanInviteCode;
import vn.giapha.membership.domain.ClanInviteUsability;

/**
 * Một mã mời dòng họ nhìn từ màn quản trị của Hội đồng.
 *
 * <p><b>Không mang mã thô, và cũng không mang băm.</b> Băm không mở được cửa nào, nhưng nó là đầu
 * vào của một phép dò ngoại tuyến nếu về sau độ dài mã bị rút ngắn — và một trường không màn hình
 * nào dùng thì không có lý do để đi qua dây.</p>
 *
 * <p>Giao diện nên hiển thị {@link #usability()} chứ <b>không</b> phải {@link #status()}:
 * {@code ACTIVE} của một mã đã quá hạn hoặc đã hết lượt không nói lên điều gì cho người đang nhìn
 * danh sách.</p>
 *
 * @param useCount      <b>chốt 3</b> — số lượt đã dùng. Đây là con số làm cả bảng này có ích: Hội
 *                      đồng thấy 400 lượt trên một dòng họ 600 người thì <i>biết</i> mà thu hồi
 * @param maxUses       trần lượt dùng; {@code null} = không đặt trần
 * @param remainingUses số lượt còn lại; {@code null} khi không đặt trần
 */
public record ClanInviteView(UUID id, String label, UUID issuedBy, String status,
                             ClanInviteUsability usability, Instant expiresAt,
                             int useCount, Integer maxUses, Integer remainingUses,
                             Instant revokedAt, String revokedReason, String note,
                             Instant createdAt, long version) {

    public static ClanInviteView from(ClanInviteCode code, Instant now) {
        return new ClanInviteView(code.id(), code.label(), code.issuedBy(), code.status().name(),
                code.usabilityAt(now), code.expiresAt(), code.useCount(), code.maxUses(),
                code.remainingUses(), code.revokedAt(), code.revokedReason(), code.note(),
                code.createdAt(), code.version());
    }
}
