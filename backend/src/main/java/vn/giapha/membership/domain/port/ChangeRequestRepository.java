package vn.giapha.membership.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.ChangeRequestStatus;
import vn.giapha.shared.vo.BranchPath;

/** Lưu trữ yêu cầu đính chính. */
public interface ChangeRequestRepository {

    Optional<ChangeRequest> byId(UUID id);

    ChangeRequest save(ChangeRequest request);

    /** Hàng đợi của người gửi — để họ theo dõi đề nghị của mình. */
    List<ChangeRequest> byRequester(UUID appUserId, int limit, int offset);

    /**
     * Hàng đợi chờ duyệt <b>trong phạm vi các chi được giao</b>.
     *
     * <p>Lọc phạm vi phải nằm trong câu SQL bằng toán tử {@code <@} của {@code ltree}, không phải
     * nạp hết rồi lọc ở Java. Hai lý do, và lý do thứ hai mới là lý do thật: nạp hết thì tốn, và
     * nạp hết thì chỉ cần một chỗ quên lọc là Trưởng chi A nhìn thấy hàng đợi của chi B.</p>
     *
     * @param scopes    các chi được giao; rỗng nghĩa là <b>không thấy gì</b>, không phải thấy tất
     * @param clanWide  {@code true} thì bỏ qua {@code scopes} và trả toàn bộ hàng đợi
     */
    List<ChangeRequest> pendingInScope(List<BranchPath> scopes, boolean clanWide,
                                       int limit, int offset);

    /** Đếm hàng đợi chờ duyệt trong phạm vi — cho badge trên giao diện. */
    long countPendingInScope(List<BranchPath> scopes, boolean clanWide);

    List<ChangeRequest> byPerson(UUID personId, ChangeRequestStatus status);
}
