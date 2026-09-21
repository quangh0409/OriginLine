package vn.giapha.membership.domain.port;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.BranchSummary;
import vn.giapha.shared.vo.BranchPath;

/**
 * Tra {@code ltree} path của một chi, và chi chính của một nhân khẩu.
 *
 * <h2>Nợ kiến trúc đã biết — đối xứng với nợ ở phía genealogy</h2>
 * {@code branch} và {@code person} thuộc context {@code genealogy}. Phân quyền theo phạm vi
 * <b>bắt buộc</b> phải biết path của đối tượng, nhưng {@code genealogy.application} không được
 * đánh dấu {@code @NamedInterface} nên {@code membership} không gọi sang được ở cấp Java mà không
 * làm {@code ModularityTests} đỏ.
 *
 * <p>Adapter Giai đoạn 1 vì vậy đọc <b>chỉ đọc</b> hai bảng đó bằng SQL. Ở cấp Java không có phụ
 * thuộc nào sang {@code genealogy} nên ranh giới module vẫn sạch. Khi {@code genealogy} công bố một
 * mặt tiền tra cứu chi (ví dụ {@code BranchLookupService} kèm {@code @NamedInterface}), adapter này
 * chuyển sang gọi service đó và bỏ SQL — chỗ phải sửa chỉ có <b>một</b>.</p>
 *
 * <p>Đây đúng là mặt gương của ghi chú trên {@code CallerIdentityPort} bên {@code genealogy}, vốn
 * đang đọc thẳng {@code app_user}/{@code branch_assignment} của {@code membership} vì lý do y hệt.</p>
 */
public interface BranchLookupPort {

    /** Path của một chi; rỗng khi chi không tồn tại hoặc đã bị xoá mềm. */
    Optional<BranchPath> pathOfBranch(UUID branchId);

    /** Chi chính của một nhân khẩu; rỗng khi nhân khẩu chưa được gắn chi nào. */
    Optional<BranchPath> branchOfPerson(UUID personId);

    /**
     * Khoá của chi chính của một nhân khẩu.
     *
     * <p>Tách khỏi {@link #branchOfPerson} vì hai lúc dùng khác nhau: lúc <b>gửi</b> yêu cầu cần
     * khoá để lưu vào {@code change_request.target_branch_id}, còn lúc <b>duyệt</b> cần path để so
     * {@code ltree}.</p>
     */
    Optional<UUID> branchIdOfPerson(UUID personId);

    /** Chi tồn tại và chưa bị xoá mềm. */
    boolean branchExists(UUID branchId);

    /**
     * {@code person.version} hiện tại — mốc khoá lạc quan mà một đề nghị đính chính dựa trên.
     *
     * <p><b>Vì sao membership cần biết con số này.</b> Một đề nghị nằm chờ Trưởng chi cả tuần rồi
     * mới được áp dụng. Không có mốc phiên bản đóng dấu <i>lúc gửi</i> thì lệnh ghi lúc duyệt là
     * một cú <b>ghi đè mù</b> lên mọi thay đổi đã xảy ra trong lúc chờ. Xem
     * {@code CorrectionPayload#BASE_VERSION_KEY}.</p>
     *
     * <p>Rỗng khi nhân khẩu không tồn tại. Người gửi vẫn có lối đi: tự gửi kèm
     * {@code _baseVersion} lấy từ {@code ETag} của lần {@code GET} hồ sơ gần nhất — và đó mới là
     * giá trị <i>đúng nhất</i>, vì nó là phiên bản họ thật sự nhìn thấy.</p>
     */
    Optional<Long> versionOfPerson(UUID personId);

    /**
     * Tên gốc của cả dòng họ — nhãn cấp 1 của {@code ltree}.
     *
     * <p>Màn nhập mã mời dòng họ cần nó để nói "Bạn đang vào <b>Dòng họ Nguyễn</b>": người nhận mã
     * qua một nhóm Zalo chuyển tiếp phải biết mã này của họ nào, nếu không thao tác đầu tiên của họ
     * trong hệ thống là một cú đoán.</p>
     *
     * <p><b>Đây là dữ liệu công khai</b>, không phải ngoại lệ riêng tư: tên dòng họ đã nằm trong
     * {@code BranchRef} mà cổng công khai trả cho Khách. Khác hẳn {@code InviteeLookupPort}, cổng
     * này không tiết lộ tên một người đang sống nào.</p>
     *
     * <p>Rỗng khi chưa có chi gốc nào — dòng họ chưa được khởi tạo.</p>
     */
    Optional<String> clanName();

    /**
     * Chi ở mức đủ để trả lời <b>"đang chờ ai"</b> — khoá, tên, loại, path.
     *
     * <p>Tách khỏi {@link #pathOfBranch} vì hai lúc dùng khác nhau: phép kiểm phạm vi chỉ cần
     * {@code ltree}, còn màn "đang chờ duyệt" cần một cái tên đọc được. Một UUID không trả lời được
     * câu hỏi ấy, và người vừa gửi đơn thì rơi vào im lặng rồi gửi lại lần hai, lần ba.</p>
     *
     * <p><b>Cổng này cố ý không trả tên hay số điện thoại của Trưởng chi.</b> Chức danh suy từ
     * {@link BranchSummary#clanTitle()} là một <i>vai</i>, không phải một con người — và một người
     * chưa được duyệt, chưa ở trong phả mà đọc được danh bạ ban quản trị là một bề mặt không ai
     * xin (design 06 §7).</p>
     *
     * <p>Rỗng khi chi không tồn tại hoặc đã bị xoá mềm.</p>
     */
    Optional<BranchSummary> summaryOfBranch(UUID branchId);
}
