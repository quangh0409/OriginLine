package vn.giapha.membership.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.PersonClaim;
import vn.giapha.membership.domain.PersonClaimStatus;
import vn.giapha.shared.vo.BranchPath;

/** Lưu trữ đơn tự nhận mình trong phả. */
public interface PersonClaimRepository {

    Optional<PersonClaim> byId(UUID id);

    /** Đơn đang chờ của một tài khoản — nhiều nhất một ({@code ux_person_claim_open_requester}). */
    Optional<PersonClaim> openForRequester(UUID appUserId);

    /**
     * Số đơn <b>đã bị từ chối</b> của một tài khoản — nền của giới hạn gửi lại.
     *
     * <p>Không giới hạn thì màn này thành cách dò đúng người bằng cách thử lần lượt: gửi đơn nhận
     * ông A, bị từ chối, gửi tiếp ông B, và cứ thế cho tới khi trúng. Đếm <i>đã bị từ chối</i> chứ
     * không đếm tổng số đơn: người tự rút đơn của mình vì gõ nhầm không phải là người đang dò.</p>
     */
    int rejectedCountOf(UUID appUserId);

    /**
     * Tổng số đơn người này đã từng gửi — nền của {@code attemptNo}.
     *
     * <p>Trưởng chi cần biết đây là lần thứ mấy: lần thứ nhất là chuyện thường, lần thứ tư trên
     * bốn nhân khẩu khác nhau là một tín hiệu hoàn toàn khác. Đếm <b>tất cả</b>, kể cả đơn đã rút —
     * khác {@link #rejectedCountOf}, vốn chỉ đếm lần bị từ chối vì nó là nền của một phép
     * <i>chặn</i>, còn đây là nền của một phép <i>hiển thị</i>.</p>
     */
    int totalCountOf(UUID appUserId);

    /**
     * Đơn đang chờ trong phạm vi chi của người duyệt.
     *
     * <p>{@code clanWide = false} và {@code scopes} rỗng phải trả rỗng. Tuyệt đối không hiểu ngược
     * "không có phạm vi nào" thành "thấy tất" — đây là lỗi kinh điển của phân quyền theo scope.</p>
     */
    List<PersonClaim> pendingInScope(List<BranchPath> scopes, boolean clanWide, int limit, int offset);

    long countPendingInScope(List<BranchPath> scopes, boolean clanWide);

    /** Đơn của chính người gọi, mới nhất trước — màn "đang chờ duyệt". */
    List<PersonClaim> byRequester(UUID appUserId, int limit, int offset);

    /**
     * Các đơn <b>khác</b> đang chờ cùng trỏ vào một nhân khẩu.
     *
     * <p>Design 07 §1.4 chốt rằng hai người cùng nhận một nhân khẩu thì Trưởng chi thấy <i>cả
     * hai</i> rồi chọn. Hệ quả: khi một đơn được duyệt, các đơn còn lại không tự biến mất — chúng
     * phải được đóng lại tường minh, kèm lý do, nếu không hàng đợi giữ mãi một đơn vĩnh viễn không
     * duyệt được (nhân khẩu đã có tài khoản).</p>
     */
    List<PersonClaim> othersClaiming(UUID personId, UUID exceptClaimId, PersonClaimStatus status);

    PersonClaim save(PersonClaim claim);
}
